package com.hep88.view

import akka.actor.typed.ActorRef
import com.hep88.{ChatClient, Client, DatabaseUtil}
import javafx.collections.{FXCollections, ObservableList, ObservableSet}
import scalafx.event.ActionEvent
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.{Alert, Button, ListView, TextArea, TextField}
import scalafx.scene.input.MouseEvent
import scalafxml.core.macros.sfxml

@sfxml
class CreateChatWindowController(
                                  private val addedUserList: ListView[String],
                                  private val userList: ListView[String]
                                ) {
  var chatClientRef: Option[ActorRef[ChatClient.Command]] = None
  var addedUsers: ObservableSet[String] = FXCollections.observableSet[String]()
  var selectedUser: String = ""

  def showAlertDialog(message: String): Unit = {
    new Alert(AlertType.Information) {
      title = "Alert"
      headerText = "Information"
      contentText = message
    }.showAndWait()
  }

  def handleUserClick(mouseEvent: MouseEvent): Unit = {
    var selectedItem = ""
    if (userList != null) {
      val selectionModel = userList.getSelectionModel
      if (selectionModel != null) {
        selectedItem = selectionModel.getSelectedItem
        if (selectedItem != null && !selectedItem.isEmpty) {
          selectedItem = userList.getSelectionModel.getSelectedItem
          selectedUser = selectedItem
        }
      }
    }
  }

  def handleAddUser(actionEvent: ActionEvent): Unit = {
    if (selectedUser.trim != "") {
      addedUsers.add(selectedUser)
      val addUsersList = FXCollections.observableArrayList(addedUsers)
      addedUserList.setItems(addUsersList)
    }
  }

  def handleConfirmChatroom(actionEvent: ActionEvent): Unit = {
    if (addedUsers.size == 0) {
      showAlertDialog("Need to have at least one user added to create a chatroom.")
    } else {
      DatabaseUtil.createChatroom(addedUsers)
      Client.closeCreateChatWindow()
    }
  }
}