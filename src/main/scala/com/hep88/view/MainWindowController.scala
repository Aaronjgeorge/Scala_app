package com.hep88.view
import scalafx.scene.control.Alert
import scalafx.scene.control.Alert.AlertType
import akka.actor.typed.ActorRef
import scalafxml.core.macros.sfxml
import scalafx.event.ActionEvent
import scalafx.scene.control.{TextField}
import com.hep88.ChatClient
import com.hep88.Client
import scalafx.application.Platform
@sfxml
class MainWindowController(private val txtUsername: TextField,
                           private val txtPassword: TextField) {

  var chatClientRef: Option[ActorRef[ChatClient.Command]] = None
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
  def onLoginResponse(success: Boolean): Unit = {
    Platform.runLater {
      if (success) {
        Client.openChatWindow()
        Client.closeMainWindow()
      } else {
        showAlertDialog("Incorrect username or password!")
      }
    }
  }
  def handleLogin(actionEvent: ActionEvent): Unit = {
    val username = txtUsername.text.value
    val password = txtPassword.text.value
    println(s"chatClientRef is: $chatClientRef")
    chatClientRef.foreach(_ ! ChatClient.LoginRequest(username, password, onLoginResponse))
  }
  def handleCreateAccount(actionEvent: ActionEvent): Unit = {
    // Request to close the main window
    Client.closeMainWindow()

    // Request to open the register window
    Client.openRegisterWindow()
  }
}