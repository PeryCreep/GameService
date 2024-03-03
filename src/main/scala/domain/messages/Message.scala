package domain.messages
import cats.effect.IO
import io.circe.{Decoder, Json}
import io.circe.generic.auto._
import io.circe.generic.semiauto.deriveDecoder
import io.circe.syntax._
import org.http4s.EntityDecoder
import org.http4s.circe.jsonOf
import slick.jdbc.PostgresProfile.api._
case class Message(gameId: Long, senderId: Long, sender: String, content: String) //todo timestamp

case class MessageDTO(senderId: Long, sender: String, content: String) {
  def toMessage(gameId: Long): Message = Message(gameId, senderId, sender, content)
}

class ChatTable(tag: Tag) extends Table[Message](tag, "messages") {

  def gameId = column[Long]("gameId")
  def senderId = column[Long]("senderId")
  def sender = column[String]("sender")
  def content = column[String]("content")
  def * = (gameId, senderId, sender, content) <> (Message.tupled, Message.unapply)
}

object MessageCompanionObject {

  implicit val messageJson: EntityDecoder[IO, Message] = jsonOf[IO, Message]

  implicit val messageDTOJsonDecoder: Decoder[MessageDTO] = deriveDecoder[MessageDTO]

  implicit class MessageJsonOps(msg: Message) {
    def toJson: Json = msg.asJson
  }

  val messages = TableQuery[ChatTable]
  def createTableIfNotExists: DBIO[Unit] =
    messages.schema.createIfNotExists

  def insertMessage(message: Message): DBIO[Int] =
    messages += message

  def getMessagesByGameId(gameId: Long): DBIO[Seq[Message]] =
    messages.filter(_.gameId === gameId).result
}
