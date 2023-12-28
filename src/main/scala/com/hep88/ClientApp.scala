package com.hep88

import scalafx.application.JFXApp
import scalafx.scene.{Scene, Parent} // Import Scene and Parent
import scalafx.stage.{Stage, StageStyle}
import scalafxml.core.{FXMLLoader, NoDependencyResolver}
import scalafx.Includes._ // Make sure this import is included
import akka.actor.typed.ActorSystem
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
    val registerStage = new Stage()
    registerStage.scene = new Scene(root)
    registerStage.show()
  }
}