import cats.effect.{ExitCode, IO, IOApp}
import domain.messages.{Message, MessageCompanionObject}
import domain.{GameCompanionObject, db}
import fs2.concurrent.Topic
import fs2.{Pipe, Stream}
import io.circe.generic.auto.exportEncoder
import io.circe.parser
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.dsl.io._
import org.http4s.implicits._
import cats.implicits.toSemigroupKOps
import domain.messages.MessageCompanionObject.MessageJsonOps
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame

object Main extends IOApp {

  private val topic = Topic[IO, Message]
  private def wsRoute(ws: WebSocketBuilder2[IO], topic: Topic[IO, Message]): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "ws" / LongVar(gameId)  =>//todo check that user can write in this game
      /** create subscribe stream for client */
      val send: Stream[IO, WebSocketFrame] = topic.subscribe(100)
        .filter(_.gameId == gameId)
        .map { msg =>
          WebSocketFrame.Text(msg.toJson.toString())
        }

      /** create pipe for input stream*/
      val receive: Pipe[IO, WebSocketFrame, Unit] = stream => stream.collect {
        case WebSocketFrame.Text(text, _) =>
          parser.parse(text)
          .map(MessageCompanionObject.messageDTOJsonDecoder.decodeJson)
      }
      .evalMap{
        case Right(Right(messageDTO)) =>
          IO.pure {
            val message = messageDTO.toMessage(gameId)
            db.run(MessageCompanionObject.insertMessage(message))
            message
          }
        case _ => IO.pure[Message](Message(1, 1, "", ""))
      }
      .through(topic.publish)


      /** check that game exists */
      IO.fromFuture(IO.pure(db.run(GameCompanionObject.checkGameExists(gameId)))).flatMap {
        case true => ws.build(send, receive)
        case false => NotFound("Такой игры не существует")
      }
  }

  override def run(args: List[String]): IO[ExitCode] = {
    /** routes for rest */
    val routes = HttpRoutes.of[IO] {
      case POST -> Root / "room" / "create"/ LongVar(userId) =>
        IO.fromFuture { IO {
          db.run(GameCompanionObject.createGame(userId))
        }}.flatMap { gameId =>
          Ok(gameId.toString)
        }

      case GET -> Root / "room" / LongVar(gameId) => //todo check that user can write in this game
        IO.fromFuture(IO.pure(db.run(MessageCompanionObject.getMessagesByGameId(gameId)))).flatMap { messages =>
          Ok(messages)
        }

    }

    def routesBuilder(topic: Topic[IO, Message]) = org.http4s.blaze.server.BlazeServerBuilder[IO]
      .bindHttp(9081, "0.0.0.0")//todo conf
      .withHttpWebSocketApp(ws => (wsRoute(ws, topic) <+> routes).orNotFound)

    val program = for {
      _ <- IO(println("Initializing database"))
      _ <- IO.fromFuture(IO(db.run(MessageCompanionObject.createTableIfNotExists)))//todo вынести в отдельный класс
      _ <- IO.fromFuture(IO(db.run(GameCompanionObject.createTableIfNotExists)))
      t <- topic //todo create queue because topic wait that all messages will be delivered
      // see https://habr.com/ru/articles/517076/
      _ <- IO.println("Server Started ")
      _ <- routesBuilder(t).serve.compile.drain
    } yield ()
    program.as(ExitCode.Success)
  }
}
