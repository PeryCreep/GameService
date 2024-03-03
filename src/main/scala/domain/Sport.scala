package domain
object Sport extends Enumeration {
  type Sport = Value

  val Basketball,
  Football,
  Volleyball,
  Soccer,
  Futsal,
  Tennis,
  `Ping Pong`,
  Badminton,
  Running = Value
}

//type SportInfo = String