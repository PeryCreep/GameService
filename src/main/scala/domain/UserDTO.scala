package domain

import cats.effect.IO
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Json}
import org.http4s.EntityDecoder
import org.http4s.circe.jsonOf

case class UserDTO(id: Long, name: String, surname: String, lastname: String, age: Int, username: String)

object UserDTO {

  implicit val jsonDecoder: Decoder[UserDTO] = Decoder.instance { h =>
    for {
      id <- h.get[Long]("id")
      name <- h.get[String]("name")
      surname <- h.get[String]("surname")
      lastname <- h.get[String]("lastname")
      age <- h.get[Int]("age")
      username <- h.get[String]("username")
    } yield UserDTO(id, name, surname, lastname, age, username)
  }

  implicit val UserDTODecoder: EntityDecoder[IO, UserDTO] = jsonOf[IO, UserDTO]


  implicit class UserDTOJson(userDTO: UserDTO) {
    def toJson: Json = userDTO.asJson
  }
}