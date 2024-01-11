package com.hep88
import java.sql.{Connection, DriverManager, ResultSet, Timestamp}
import com.typesafe.config.ConfigFactory
import javafx.collections.{FXCollections, ObservableList, ObservableSet}
import java.sql.SQLException

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import scala.collection.mutable.ListBuffer

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
      val stmt = conn.prepareStatement("SELECT COUNT(*) FROM user WHERE username = ?")
      stmt.setString(1, username)
      val rs: ResultSet = stmt.executeQuery()
      rs.next() && rs.getInt(1) > 0
    } finally {
      conn.close()
    }
  }

  def createUser(username: String, password: String): Boolean = {
    print("Started")
    val conn = getConnection
    try {
      val stmt = conn.prepareStatement("INSERT INTO user (username, password) VALUES (?, ?)")
      stmt.setString(1, username)
      stmt.setString(2, password)

      stmt.executeUpdate() > 0
    } catch {
      case e: Exception =>
        e.printStackTrace()
        false
    } finally {
      print("User created")
      conn.close()
    }
  }

  def validateUser(username: String, password: String): (Boolean, Int) = {
    val conn = getConnection
    try {
      val stmt = conn.prepareStatement("SELECT user_id FROM user WHERE username = ? AND password = ?")
      stmt.setString(1, username)
      stmt.setString(2, password)
      val rs: ResultSet = stmt.executeQuery()

      if (rs.next()) {
        val validatedUserId: Int = (rs.getInt("user_id"))
        val updateStmt = conn.prepareStatement("UPDATE user SET onlineStatus = ? WHERE username = ? AND password = ?")

        updateStmt.setInt(1, 1)
        updateStmt.setString(2, username)
        updateStmt.setString(3, password)

        val rowsUpdated: Int = updateStmt.executeUpdate()

        if (rowsUpdated > 0) {
          println("Updated online status to true")
        } else {
          println("Failed to update online status")
        }

        (true, validatedUserId)
      } else {
        (false, 0)
      }
    } finally {
      conn.close()
    }
  }

  def searchUser(query: String): Seq[String] = {
    val conn = getConnection
    try {
      val stmt = conn.prepareStatement("SELECT username FROM user WHERE username LIKE ?")
      stmt.setString(1, query + "%") // Use LIKE for partial matches
      val rs: ResultSet = stmt.executeQuery()

      val users = new ListBuffer[String]()
      while (rs.next()) {
        users += rs.getString("username")
      }
      users.toList
    } finally {
      conn.close()
    }
  }
  def addFriend(currentUserName: String, friendUsername: String): Boolean = {
    val conn = getConnection
    try {
      // Step 1: Find the user ID of the current user and the friend
      val findCurrentUserStmt = conn.prepareStatement("SELECT user_id FROM user WHERE username LIKE ?")
      findCurrentUserStmt.setString(1, currentUserName)
      val rsCurrentUser = findCurrentUserStmt.executeQuery()

      val findFriendUserStmt = conn.prepareStatement("SELECT user_id FROM user WHERE username LIKE ?")
      findFriendUserStmt.setString(1, friendUsername)
      val rsFriendUser = findFriendUserStmt.executeQuery()

      var currentUserId: Option[Int] = None
      var friendUserId: Option[Int] = None

      if (rsCurrentUser.next()) {
        currentUserId = Some(rsCurrentUser.getInt("user_id"))
      }

      if (rsFriendUser.next()) {
        friendUserId = Some(rsFriendUser.getInt("user_id"))
      }

      if (currentUserId.isDefined && friendUserId.isDefined) {
        // Step 2: Add the friend's user ID to the user's list of friends (both ways)
        val addStmt = conn.prepareStatement("INSERT INTO friendships (user_id, friend_id) VALUES (?, ?), (?, ?)")
        addStmt.setInt(1, currentUserId.get)
        addStmt.setInt(2, friendUserId.get)
        addStmt.setInt(3, friendUserId.get)
        addStmt.setInt(4, currentUserId.get)
        addStmt.executeUpdate() > 0 // Returns true if the rows are inserted
      } else {
        false // User or friend username not found
      }
    } catch {
      case e: SQLException =>
        println("SQLException occurred: " + e.getMessage)
        false
      case e: Exception =>
        println("Exception occurred: " + e.getMessage)
        false
    } finally {
      conn.close()
    }
  }

  def deleteFriend(currentUserName: String, friendUsername: String): Boolean = {
    val conn = getConnection
    try {
      // Step 1: Find the user ID of the current user and the friend
      val findCurrentUserStmt = conn.prepareStatement("SELECT user_id FROM user WHERE username = ?")
      findCurrentUserStmt.setString(1, currentUserName)
      val rsCurrentUser = findCurrentUserStmt.executeQuery()

      val findFriendUserStmt = conn.prepareStatement("SELECT user_id FROM user WHERE username = ?")
      findFriendUserStmt.setString(1, friendUsername)
      val rsFriendUser = findFriendUserStmt.executeQuery()

      var currentUserId: Option[Int] = None
      var friendUserId: Option[Int] = None

      if (rsCurrentUser.next()) {
        currentUserId = Some(rsCurrentUser.getInt("user_id"))
      }

      if (rsFriendUser.next()) {
        friendUserId = Some(rsFriendUser.getInt("user_id"))
      }

      if (currentUserId.isDefined && friendUserId.isDefined) {
        // Step 2: Delete the friendship from the friendships table (both ways)
        val deleteStmt = conn.prepareStatement(
          "DELETE FROM friendships WHERE (user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?)")
        deleteStmt.setInt(1, currentUserId.get)
        deleteStmt.setInt(2, friendUserId.get)
        deleteStmt.setInt(3, friendUserId.get)
        deleteStmt.setInt(4, currentUserId.get)
        deleteStmt.executeUpdate() > 0 // Returns true if the rows are deleted
      } else {
        false // User or friend username not found
      }
    } catch {
      case e: SQLException =>
        println("SQLException occurred: " + e.getMessage)
        false
      case e: Exception =>
        println("Exception occurred: " + e.getMessage)
        false
    } finally {
      conn.close()
    }
  }

  def getOnlineFriends(currentUserId: Int): ObservableList[String] = {
    val conn = getConnection
    try {
      val onlineUsers = FXCollections.observableArrayList[String]()

      // SQL query to find online friends of the current user
      val query = """
      SELECT u.user_id, u.username
      FROM user u
      INNER JOIN friendships f ON u.user_id = f.friend_id
      WHERE u.onlineStatus = 1 AND u.user_id <> ? AND f.user_id = ?
    """

      val stmt = conn.prepareStatement(query)
      stmt.setInt(1, currentUserId)
      stmt.setInt(2, currentUserId)
      val rs = stmt.executeQuery()

      while (rs.next()) {
        val userId = rs.getInt("user_id")
        val username = rs.getString("username")
        onlineUsers.add(s"$userId;$username")
      }

      onlineUsers
    } catch {
      case e: SQLException =>
        println("SQLException occurred: " + e.getMessage)
        FXCollections.observableArrayList[String]() // Return an empty list in case of exception
      case e: Exception =>
        println("Exception occurred: " + e.getMessage)
        FXCollections.observableArrayList[String]() // Return an empty list in case of exception
    } finally {
      conn.close()
    }
  }

  def getUsers(currentUserId: Int): ObservableList[String] = {
    val conn = getConnection
    try {
      val users = FXCollections.observableArrayList[String]()
      val query = "SELECT user_id, username FROM user WHERE user_id <> ?"
      val stmt = conn.prepareStatement(query)
      stmt.setInt(1, currentUserId)
      val rs = stmt.executeQuery()

      while (rs.next()) {
        val userId = rs.getInt("user_id")
        val username = rs.getString("username")
        users.add(s"$userId;$username")
      }

      users
    } finally {
      conn.close()
    }
  }

  def goOffline(): Unit = {
    val conn = getConnection
    try {
      val updateStmt = conn.prepareStatement("UPDATE user SET onlineStatus = ? WHERE user_id = ?")

      println(s"The user id is: ${ChatClient.userId}")
      updateStmt.setInt(1, 0)
      updateStmt.setInt(2, ChatClient.userId)

      val rowsUpdated: Int = updateStmt.executeUpdate()

      if (rowsUpdated > 0) {
        println("Went offline")
      } else {
        println("Failed to go offline")
      }
    } finally {
      conn.close()
    }
  }
  def deleteChatroom(selectedChat: String): Boolean = {
    val groupId = selectedChat.takeWhile(_.isDigit).toInt // Extracting group_id from the beginning of the string
    val conn = getConnection
    try {
      // Start a transaction
      conn.setAutoCommit(false)

      // Step 1: Delete from group_members table
      val deleteGroupMembersStmt = conn.prepareStatement("DELETE FROM group_members WHERE group_id = ?")
      deleteGroupMembersStmt.setInt(1, groupId)
      deleteGroupMembersStmt.executeUpdate()

      // Step 2: Delete from chat_group table
      val deleteChatGroupStmt = conn.prepareStatement("DELETE FROM chat_group WHERE group_id = ?")
      deleteChatGroupStmt.setInt(1, groupId)
      deleteChatGroupStmt.executeUpdate()

      // Commit the transaction
      conn.commit()
      true
    } catch {
      case e: SQLException =>
        println("SQLException occurred: " + e.getMessage)
        conn.rollback() // Rollback in case of an exception
        false
      case e: Exception =>
        println("Exception occurred: " + e.getMessage)
        conn.rollback()
        false
    } finally {
      conn.setAutoCommit(true) // Reset auto commit to true
      conn.close()
    }
  }
  def createChatroom(addedUsers: ObservableSet[String]): Unit = {
    val conn = getConnection
    val currentTimestamp: Timestamp = Timestamp.valueOf(LocalDateTime.now()) // use
    try {
      val numberOfUsers = addedUsers.size() // use
      val idList = ListBuffer[Int]()
      val usernameList = ListBuffer[String]()
      var groupName = s"${ChatClient.userId}" // use
      var groupType = "" // use

      if (numberOfUsers > 1) {
        groupType = "group"
      } else {
        groupType = "one"
      }

      val usernameStmt = conn.prepareStatement("SELECT username FROM user WHERE user_id = ?")
      usernameStmt.setInt(1, ChatClient.userId)
      val usernameResult = usernameStmt.executeQuery()
      while (usernameResult.next()) {
        groupName = usernameResult.getString("username")
      }

      addedUsers.forEach { user =>
        val parts = user.split(";")
        if (parts.length == 2) {
          val id = parts(0).toInt
          val username = parts(1)
          idList += id
          usernameList += username
        }
      }
      groupName = (groupName +: usernameList).mkString(";")

      // Insert into chat_group table
      val stmt = conn.prepareStatement("INSERT INTO chat_group (group_name, group_type, number_of_members, timeCreated) VALUES (?, ?, ?, ?)")
      stmt.setString(1, groupName)
      stmt.setString(2, groupType)
      stmt.setInt(3, numberOfUsers)
      stmt.setTimestamp(4, currentTimestamp)
      val creationResult = stmt.executeUpdate() > 0
      println(s"Result of chatroom creation: $creationResult")
    } finally {
      conn.close()
      insertGroupMembers(addedUsers, currentTimestamp)
    }
  }

  def insertGroupMembers(addedUsers: ObservableSet[String], currentTimestamp: Timestamp): Unit = {
    val conn = getConnection
    var groupId: Int = -1
    val timestampRounded = roundTimestamp(currentTimestamp)
    try {
      println(s"1, $addedUsers, $timestampRounded, $currentTimestamp")
      val query = conn.prepareStatement("SELECT group_id FROM chat_group WHERE timeCreated = ?")
      query.setTimestamp(1, timestampRounded)
      val rs = query.executeQuery()
      while (rs.next()) {
        groupId = rs.getInt("group_id")
        println("2")
      }

      addedUsers.forEach { user =>
        val parts = user.split(";")
        if (parts.length == 2) {
          val id = parts(0).toInt
          val groupMemberStmt = conn.prepareStatement("INSERT INTO group_members (group_id, user_id) VALUES (?, ?)")
          groupMemberStmt.setInt(1, groupId)
          groupMemberStmt.setInt(2, id)
          val insertResult = groupMemberStmt.executeUpdate() > 0
          println(s"Result of group member insertion: $insertResult")
        }
      }

      val insertSelfStmt = conn.prepareStatement("INSERT INTO group_members (group_id, user_id) VALUES (?, ?)")
      insertSelfStmt.setInt(1, groupId)
      insertSelfStmt.setInt(2, ChatClient.userId)
      val insertSelfResult = insertSelfStmt.executeUpdate() > 0
      println(s"Result of group member self insertion: $insertSelfResult")
    } finally {
      conn.close()
    }
  }

  def roundTimestamp(timestamp: Timestamp): Timestamp = {
    val localDateTime = timestamp.toLocalDateTime
    val milliseconds = localDateTime.getNano / 1000000 // Convert nanoseconds to milliseconds

    val roundedDateTime =
      if (milliseconds >= 500)
        localDateTime.plus(1, ChronoUnit.SECONDS).truncatedTo(ChronoUnit.SECONDS)
      else
        localDateTime.truncatedTo(ChronoUnit.SECONDS)

    Timestamp.valueOf(roundedDateTime)
  }

  def populateChatRoomList(): ObservableSet[String] = {
    val conn = getConnection
    try {
      val chatRooms: ObservableSet[String] = FXCollections.observableSet[String]()
      val chatRoomIds: ObservableSet[Int] = FXCollections.observableSet[Int]()
      val stmt = conn.prepareStatement("SELECT group_id FROM group_members WHERE user_id = ?")
      stmt.setInt(1, ChatClient.userId)
      val rs = stmt.executeQuery()

      while (rs.next()) {
        chatRoomIds.add(rs.getInt("group_id"))
      }

      if (chatRoomIds.size() > 0) {
        chatRoomIds.forEach {id =>
          val query = conn.prepareStatement("SELECT group_name FROM chat_group WHERE group_id = ?")
          query.setInt(1, id)
          val rsQuery = query.executeQuery()

          while (rsQuery.next()) {
            val groupName = rsQuery.getString("group_name")
            val chatRoomString = s"$id;$groupName"
            chatRooms.add(chatRoomString)
          }
        }
      }

      chatRooms
    } finally {
      conn.close()
    }
  }

  def openChatRoom(chatRoomId: Int): ObservableList[String] = {
    val conn = getConnection
    try {
      var messageId: Int = -1 // 0
      var senderId: Int = -1 // 1
      var content: String = "" // 2
      var timestamp: Timestamp = Timestamp.valueOf(LocalDateTime.now()) // 3

      val messages: ObservableList[String] = FXCollections.observableArrayList[String]()
      val messageStmt = conn.prepareStatement("SELECT * FROM message WHERE group_id = ?")
      messageStmt.setInt(1, chatRoomId)
      val messageResult = messageStmt.executeQuery()
      while (messageResult.next()) {
        messageId = messageResult.getInt("message_id")
        senderId = messageResult.getInt("sender_id")
        content = messageResult.getString("content")
        timestamp = messageResult.getTimestamp("timestamp")
        val message = s"${messageId.toString};${senderId.toString};$content;${timestamp.toString}"
        messages.add(message)
      }

      messages
    } finally {
      conn.close()
    }
  }

  def sendMessage(message: String): Boolean = {
    val conn = getConnection
    try {
      val senderId = ChatClient.userId
      val groupId = ChatClient.currentChatRoomId
      val content = message
      val currentTimestamp: Timestamp = Timestamp.valueOf(LocalDateTime.now())

      val messageInsertStmt = conn.prepareStatement("INSERT INTO message (sender_id, group_id, content, timestamp) VALUES (?, ?, ?, ?)")
      messageInsertStmt.setInt(1, senderId)
      messageInsertStmt.setInt(2, groupId)
      messageInsertStmt.setString(3, content)
      messageInsertStmt.setTimestamp(4, currentTimestamp)
      val messageInsertResult = messageInsertStmt.executeUpdate() > 0

      messageInsertResult
    } finally {
      conn.close()
    }
  }
}