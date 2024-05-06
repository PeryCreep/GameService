import Main.getAllMessageInGame
import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import configuration.ServerConf
import domain.messages.MessageCompanionObject.MessageJsonOps
import domain.messages.{Message, MessageCompanionObject}
import domain.{Game, GameRepository, UserDTO, db}
import fs2.concurrent.Topic
import fs2.{Pipe, Stream}
import io.circe.generic.auto.exportEncoder
import io.circe.parser
import io.circe.parser._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.http4s.{HttpRoutes, Request, Response, headers}
import org.typelevel.ci.CIString
import pdi.jwt.{JwtAlgorithm, JwtCirce}

object Main extends IOApp {

  private val topic = Topic[IO, Message]
  private val serverConf = ServerConf.instance

  private val jwtKey = serverConf.jwtKey

  private def wsRoute(ws: WebSocketBuilder2[IO], topic: Topic[IO, Message]): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case  req @ GET -> Root / "ws" / LongVar(gameId) =>
      for {
        token <- extractTokenFromHeader(req)
        userOpt <- IO.pure(decryptToken(token))
        gameOpt <- getGameById(gameId)
        userInGameOpt <- IO.pure(checkUserInGame(gameOpt, userOpt))
        response <- userInGameOpt.map { userInGame =>
          if(userInGame) buildWebSocket(gameId, ws, topic) else Forbidden("Пользователя нет в данной игре!")
        }.getOrElse(NotFound("Такой игры не существует или в токене не хранится необходимая информация"))
      } yield response
  }

  private def checkUserInGame(gameOpt: Option[Game], userOpt: Option[UserDTO]): Option[Boolean] = {
    (gameOpt, userOpt).tupled.map { case (game, user) =>
      game.participants.contains(user.id)
    }
  }

  private def extractTokenFromHeader(req: Request[IO]): IO[String] = {
    headers.Authorization
    req.headers.get(CIString("token"))
      .map(_.head.value)
      .fold(IO.raiseError[String](new Exception("Токен не найден в заголовке запроса!")))(x => IO.pure(x))
  }

  private def decryptToken(token: String): Option[UserDTO] = {

    for {
      claim <- JwtCirce.decode(token, jwtKey, Seq(JwtAlgorithm.HS256)).toOption
      userDataJson <- parse(claim.content).toOption
      userData <- userDataJson.as[UserDTO].toOption
    } yield userData
  }

  private def buildWebSocket(gameId: Long, ws: WebSocketBuilder2[IO], topic: Topic[IO, Message]): IO[Response[IO]] = {
    /** create subscribe stream for client */
    val send: Stream[IO, WebSocketFrame] = topic.subscribe(100)
      .filter(_.gameId == gameId)
      .map { msg =>
        WebSocketFrame.Text(msg.toJson.toString())
      }

    /** create pipe for input stream */
    val receive: Pipe[IO, WebSocketFrame, Unit] = stream => stream.collect {
        case WebSocketFrame.Text(text, _) =>
          parser.parse(text)
            .map(MessageCompanionObject.messageDTOJsonDecoder.decodeJson)
      }
      .evalMap {
        case Right(Right(messageDTO)) =>
          IO.pure {
            val message = messageDTO.toMessage(gameId)
            db.run(MessageCompanionObject.insertMessage(message))
            message
          }
        case _ => IO.pure[Message](Message(1, 1, "", ""))
      }
      .through(topic.publish)

    ws.build(send, receive)
  }

  private def getGameById(gameId: Long): IO[Option[Game]] =
    IO.fromFuture(IO.pure(db.run(GameRepository.getGameById(gameId))))

  private def getAllMessageInGame(gameId: Long): IO[Seq[Message]] = {
    IO.fromFuture(IO.pure(db.run(MessageCompanionObject.getMessagesByGameId(gameId))))
  }


  override def run(args: List[String]): IO[ExitCode] = {
    /** routes for rest */
    val routes = HttpRoutes.of[IO] { //todo добавить мониторинг нагрузки
      case req @ POST -> Root / "game" / "create" =>
        IO.fromFuture {
          for {
            userId <- req.as[Long]
          } yield {
            db.run(GameRepository.createGame(userId))
          }
        }.flatMap { gameId =>
          Ok(gameId.toString)
        }

      case req @ GET -> Root / "game" / LongVar(gameId) =>
        for {
          token <- extractTokenFromHeader(req)
          userOpt <- IO.pure(decryptToken(token))
          gameOpt <- getGameById(gameId)
          userInGameOpt <- IO.pure(checkUserInGame(gameOpt, userOpt))
          response <- userInGameOpt.map { userInGame =>
            if(userInGame)
              Ok(getAllMessageInGame(gameId))
            else Forbidden("Пользователя нет в данной игре!")
          }.getOrElse(NotFound("Такой игры не существует или в токене не хранится необходимая информация"))
        } yield response

      case req @ POST -> Root / "game" / LongVar(gameId) / "participants" =>
        for {
          participantId <- req.as[Long]
          result <- IO.fromFuture(IO.pure(db.run(GameRepository.addParticipant(gameId, participantId))))
          response <- if (result > 0) Ok("Participant added to the game")
          else NotFound("Game not found")
        } yield response
    }

    def routesBuilder(topic: Topic[IO, Message]) = org.http4s.blaze.server.BlazeServerBuilder[IO]
      .bindHttp(serverConf.port, serverConf.host)
      .withHttpWebSocketApp(ws => (wsRoute(ws, topic) <+> routes).orNotFound)

    val program = for {
      _ <- IO(println("Initializing database"))
      _ <- IO.fromFuture(IO(db.run(MessageCompanionObject.createTableIfNotExists))) //todo вынести в отдельный класс
      _ <- IO.fromFuture(IO(db.run(GameRepository.createTableIfNotExists)))
      t <- topic //todo create queue because topic wait that all messages will be delivered
      // see https://habr.com/ru/articles/517076/
      _ <- IO.println("Server Started ")
      _ <- routesBuilder(t).serve.compile.drain
    } yield ()
    program.as(ExitCode.Success)
  }
}
