package com.hep88

import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.scene.{Parent, Scene}
import scalafx.stage.{Stage, StageStyle}
import scalafxml.core.{FXMLLoader, NoDependencyResolver}
import akka.actor.typed.ActorSystem
import javafx.collections.{FXCollections, ObservableList}
import javafx.scene.control.ListView
import scalafx.collections.ObservableBuffer

object Client extends JFXApp {
  // Create an Actor System and ChatClient ActorRef
  val greeterMain: ActorSystem[ChatClient.Command] = ActorSystem(ChatClient(), "ChatSystem")
  greeterMain ! ChatClient.start

  val loader = new FXMLLoader(null, NoDependencyResolver)
  loader.load(getClass.getResourceAsStream("view/MainWindow.fxml"))

  // Obtain the controller and pass the chatClientRef
  val controller = loader.getController[com.hep88.view.MainWindowController#Controller]()
  controller.chatClientRef = Some(greeterMain)

  val rootPane: scalafx.scene.layout.Pane = loader.getRoot[javafx.scene.layout.Pane]()

  stage = new JFXApp.PrimaryStage() {
    initStyle(StageStyle.Undecorated)
    scene = new Scene(rootPane)
  }

  val viewingStage = new Stage()

  private var onlineList: ListView[String] = _
  private var userList: ListView[String] = _
  private var chatList: ListView[String] = _
  private var chatMessages: ListView[String] = _


  def openRegisterWindow(): Unit = {
    val loader = new FXMLLoader(getClass.getResource("view/RegisterWindow.fxml"), NoDependencyResolver)
    val root = loader.load[javafx.scene.Parent]
    val registerStage = new Stage()
    registerStage.scene = new Scene(root)

    val controller = loader.getController[com.hep88.view.RegisterWindowController#Controller]()
    controller.currentStage = registerStage // Add this line

    registerStage.show()
  }

  def closeMainWindow(): Unit = {
    stage.close()
  }

  def exitWindow(): Unit={
    System.exit(0)
  }
  def openMainWindow(): Unit = {
    val loader = new FXMLLoader(getClass.getResource("view/MainWindow.fxml"), NoDependencyResolver)
    val root = loader.load[javafx.scene.Parent]
    val registerStage = new Stage()
    registerStage.scene = new Scene(root)
    registerStage.show()
  }

  def openChatWindow(): Unit = {
    val loader = new FXMLLoader(getClass.getResource("view/ChatWindow.fxml"), NoDependencyResolver)
    val root = loader.load[javafx.scene.Parent]
    val controller = loader.getController[com.hep88.view.ChatWindowController#Controller]()
    controller.chatClientRef = Some(greeterMain)
    val registerStage = new Stage()

    onlineList = loader.getNamespace.get("onlineList").asInstanceOf[ListView[String]]
    chatList = loader.getNamespace.get("chatList").asInstanceOf[ListView[String]]
    chatMessages = loader.getNamespace.get("chatView").asInstanceOf[ListView[String]]
    val chatRoomsSet = DatabaseUtil.populateChatRoomList()
    val chatRoomsList = FXCollections.observableArrayList[String](chatRoomsSet)
    chatList.setItems(chatRoomsList)
    registerStage.scene = new Scene(root)
    registerStage.show()
  }

  def populateOnlineFriendsList(receivedUserId: Int): Unit = {
    val onlineFriends: ObservableList[String] = DatabaseUtil.getOnlineFriends(receivedUserId)
    val usernames = onlineFriends.map(_.split(";")(1))
    onlineList.setItems(usernames)
  }

  def openCreateChatWindow(): Unit = {
    val loader = new FXMLLoader(getClass.getResource("view/CreateChatWindow.fxml"), NoDependencyResolver)
    val root = loader.load[javafx.scene.Parent]
    val controller = loader.getController[com.hep88.view.CreateChatWindowController#Controller]()
    controller.chatClientRef = Some(greeterMain)

    val users = DatabaseUtil.getUsers(ChatClient.userId)
    userList = loader.getNamespace.get("userList").asInstanceOf[ListView[String]]
    userList.setItems(users)

    viewingStage.scene = new Scene(root)
    viewingStage.show()
  }

  def closeCreateChatWindow(): Unit = {
    // get info from database and add to listview
    val chatRoomsSet = DatabaseUtil.populateChatRoomList()
    val chatRoomsList = FXCollections.observableArrayList[String](chatRoomsSet)
    chatList.setItems(chatRoomsList)
    viewingStage.close()
  }

  def updateChat(groupId: Int): Unit = {
    val messages = DatabaseUtil.openChatRoom(groupId)
    val formattedMessages: ObservableList[String] = FXCollections.observableArrayList[String]()
    messages.forEach { message =>
      val messageParts = message.split(";")
      if (messageParts.length > 1) {
        val content = messageParts(2)
        val timestamp = messageParts(3)
        formattedMessages.add(s"[$timestamp]: $content")
      } else {
        formattedMessages.add("Corrupted message format")
      }
    }
    chatMessages.setItems(formattedMessages)
  }
}