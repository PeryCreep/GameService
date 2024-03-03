package domain
import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape
case class Game(id: Option[Long], participants: Seq[Long])

class GameTable(tag: Tag) extends Table[Game](tag, "games") {
  // MappedColumnType для преобразования Seq в JSON и обратно
  implicit val seqStringColumnType: BaseColumnType[Seq[Long]] =
    MappedColumnType.base[Seq[Long], String](
      seq => seq.mkString(","),
      str => str.split(",").map(x => x.toLong).toSeq
    )

  def id: Rep[Long] = column[Long]("id", O.AutoInc, O.PrimaryKey)

  def participants: Rep[Seq[Long]] = column[Seq[Long]]("participants")
  override def * : ProvenShape[Game] = (id.?, participants) <> (Game.tupled, Game.unapply)
}
object GameCompanionObject {

  val games = TableQuery[GameTable]
  def createTableIfNotExists: DBIO[Unit] =
    games.schema.createIfNotExists

  def checkGameExists(gameId: Long): DBIO[Boolean] =
    games.filter(_.id === gameId).exists.result

  def createGame(userId: Long): DBIO[Long] =
    games returning games.map(_.id) += Game(None,Seq(userId))

  def getGames: DBIO[Seq[Game]] =
    games.result
}
