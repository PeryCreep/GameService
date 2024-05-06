package domain

import domain.GameRepository.seqStringColumnType
import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape

import scala.concurrent.ExecutionContext.Implicits.global

case class Game(id: Option[Long], participants: Seq[Long])

class GameTable(tag: Tag) extends Table[Game](tag, "games") { //todo вынестри в отдельный файл

  def id: Rep[Long] = column[Long]("id", O.AutoInc, O.PrimaryKey)

  def participants: Rep[Seq[Long]] = column[Seq[Long]]("participants")

  override def * : ProvenShape[Game] = (id.?, participants) <> (Game.tupled, Game.unapply)

}

object GameRepository {

  val games = TableQuery[GameTable]

  def createTableIfNotExists: DBIO[Unit] =
    games.schema.createIfNotExists

  def getGameById(gameId: Long): DBIO[Option[Game]] =
    games.filter(_.id === gameId).result.headOption

  def createGame(userId: Long): DBIO[Long] =
    games returning games.map(_.id) += Game(None, Seq(userId))

  // MappedColumnType для преобразования Seq в JSON и обратно
  implicit val seqStringColumnType: BaseColumnType[Seq[Long]] =
    MappedColumnType.base[Seq[Long], String](
      seq => seq.mkString(","),
      str => str.split(",").map(x => x.toLong).toSeq
    )

  def addParticipant(gameId: Long, participantId: Long): DBIO[Int] = {
    import slick.jdbc.PostgresProfile.api._
    val query = for {
      game <- games if game.id === gameId
    } yield game

    query.result.head.flatMap { game =>
      val updatedParticipants = game.participants :+ participantId
      val updateQuery = games.filter(_.id === gameId).map(_.participants).update(updatedParticipants)
      updateQuery
    }.transactionally
  }
}
