import slick.jdbc.PostgresProfile.api._
package object domain {
  val db = Database.forConfig("gameDB")
}
