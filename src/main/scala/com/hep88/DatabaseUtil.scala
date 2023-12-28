package com.hep88
import java.sql.{Connection, DriverManager, ResultSet}
import com.typesafe.config.ConfigFactory

object DatabaseUtil {
  val config = ConfigFactory.load()
  val url = config.getString("db.url")
  val user = config.getString("db.username")
  val pass = config.getString("db.password")


  // Ensure the JDBC driver is loaded
  Class.forName(config.getString("db.driver"))

  def getConnection: Connection = DriverManager.getConnection(url,user,pass)

  def userExists(username: String): Boolean = {
    val conn = getConnection
    try {
      val stmt = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE username = ?")
      stmt.setString(1, username)
      val rs: ResultSet = stmt.executeQuery()
      rs.next() && rs.getInt(1) > 0
    } finally {
      conn.close()
    }
  }

  def createUser(username: String, password: String): Boolean = {
    val conn = getConnection
    try {
      val stmt = conn.prepareStatement("INSERT INTO users (username, password) VALUES (?, ?)")
      stmt.setString(1, username)
      stmt.setString(2, password)
      stmt.executeUpdate() > 0
    } finally {
      print("User created")
      conn.close()
    }
  }

  def validateUser(username: String, password: String): Boolean = {
    val conn = getConnection
    try {
      val stmt = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE username = ? AND password = ?")
      stmt.setString(1, username)
      stmt.setString(2, password)
      val rs: ResultSet = stmt.executeQuery()
      rs.next() && rs.getInt(1) > 0
    } finally {
      conn.close()
    }
  }
}