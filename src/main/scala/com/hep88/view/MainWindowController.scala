package com.hep88.view
import scalafx.scene.control.Alert
import scalafx.scene.control.Alert.AlertType
import akka.actor.typed.ActorRef
import scalafxml.core.macros.sfxml
import scalafx.event.ActionEvent
import scalafx.scene.control.TextField
import com.hep88.{ChatClient, ChatServer, Client}
import scalafx.application.Platform
import scalafx.stage.Stage
@sfxml
class MainWindowController(private val txtUsername: TextField,
                           private val txtPassword: TextField,
                           var currentStage: Stage) {

  var chatClientRef: Option[ActorRef[ChatClient.Command]] = None
  var chatServerRef: Option[ActorRef[ChatServer.Command]] = None

  def showAlertDialog(message: String): Unit = {
    new Alert(AlertType.Information) {
      title = "Alert"
      headerText = "Information"
      contentText = message
    }.showAndWait()
  }
  def handleExit(): Unit={
    Client.exitWindow()
  }
  def onLoginResponse(success: Boolean, validatedUserId: Int): Unit = {
    Platform.runLater {
      if (success) {
        chatClientRef.foreach(_ ! ChatClient.SetUserId(validatedUserId))
        chatServerRef.foreach(_ ! ChatServer.FindClients)
        Client.closeMainWindow()
        Client.openChatWindow()
      } else {
        showAlertDialog("Incorrect username or password!")
      }
    }
  }
  def onServerRefResponse(receivedServerRef: Option[ActorRef[ChatServer.Command]]): Unit = {
    println(s"Received THIS server ref: $receivedServerRef")
    Platform.runLater{
      if (receivedServerRef.isDefined) {
        chatServerRef = receivedServerRef
      } else {
        println("Not received!")
      }
    }
  }
  def handleLogin(actionEvent: ActionEvent): Unit = {
    val username = txtUsername.text.value
    val password = txtPassword.text.value
    println(s"chatClientRef is: $chatClientRef")
    chatClientRef.foreach(_ ! ChatClient.ServerRefRequest(onServerRefResponse))
    chatClientRef.foreach(_ ! ChatClient.LoginRequest(username, password, onLoginResponse))
  }
  def handleCreateAccount(actionEvent: ActionEvent): Unit = {
    // Request to close the main window
    Client.closeMainWindow()

    // Request to open the register window
    Client.openRegisterWindow()
  }
}