package com.hep88.view

import akka.actor.typed.ActorRef
import com.hep88.{ChatClient, Client}
import scalafx.event.ActionEvent
import scalafx.scene.control.TextField
import scalafx.stage.Stage
import scalafxml.core.macros.sfxml

@sfxml
class RegisterWindowController(private val txtUsername: TextField,
                               private val txtPassword: TextField,
                               var currentStage: Stage) {
  var chatClientRef: Option[ActorRef[ChatClient.Command]] = None

  def handleRegister(actionEvent: ActionEvent): Unit = {
    // Your logic for handling the registration process goes here
    val username = txtUsername.text.value
    val password = txtPassword.text.value

    println(s"chatClientRef is: $chatClientRef")
    chatClientRef foreach (_ ! ChatClient.RegisterRequest(username, password))
    println(s"Registering user: $username with password: $password")
    currentStage.close()
    Client.openMainWindow()
    // Optionally, you can open another window or perform other actions
  }


}
