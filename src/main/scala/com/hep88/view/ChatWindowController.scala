package com.hep88.view
import scalafx.scene.control.{Alert, Button, ListView, TextArea, TextField}
import scalafx.scene.control.Alert.AlertType
import akka.actor.typed.ActorRef
import scalafxml.core.macros.sfxml
import scalafx.event.ActionEvent
import com.hep88.{ChatClient, Client, DatabaseUtil}
import scalafx.application.Platform
import scalafx.scene.input.MouseEvent
import scalafx.stage.Stage
import javafx.collections.{FXCollections, ObservableList}
import javafx.scene.input.{KeyCode, KeyEvent}

@sfxml
class ChatWindowController (
                            private val friendList: ListView[String],
                            private val searchFriends: TextField,
                            private val addFriend: Button,
                            private val onlineList: ListView[String],
                            private val chatList: ListView[String],
                            private val createRoom: Button,
                            private val chatDelete: Button,
                            private val removeFriend: Button,
                            private val chatView: ListView[String],
                            private val msgArea: TextArea,
                            private val quitClient: Button,
                            var currentStage: Stage){
  var chatClientRef: Option[ActorRef[ChatClient.Command]] = None

  def handleSearch(actionEvent: ActionEvent): Unit = {
    val searchQuery = searchFriends.text.value
    chatClientRef.foreach(_ ! ChatClient.SearchRequest(searchQuery, updateFriendList))
  }

  private def updateFriendList(results: Seq[String]): Unit = {
    Platform.runLater {
      friendList.items = scalafx.collections.ObservableBuffer(results)
    }
  }

  def handleAddFriend(actionEvent: ActionEvent): Unit = {
    val selectionModel = friendList.selectionModel()
    val selectedFriendOption = Option(selectionModel.getSelectedItem)

    selectedFriendOption match {
      case Some(selectedFriend) =>
        chatClientRef.foreach(_ ! ChatClient.AddFriendRequest(selectedFriend, handleAddFriendResponse))
      case None =>
        // Handle the case where no friend is selected
        println("No friend selected.")
    }
  }
  def handleAddFriendResponse(isSuccess: Boolean): Unit = {
    if (isSuccess) {
      Platform.runLater(() => {
        // Update UI to reflect success
        println("Friend added successfully.")
      })
    } else {
      Platform.runLater(() => {
        // Update UI to reflect failure
        println("Failed to add friend.")
      })
    }
  }
  var selectedChat: String = ""

  def handleChatClick(mouseEvent: MouseEvent): Unit = {
    var selectedItem = ""
    if (chatList != null) {
      val selectionModel = chatList.getSelectionModel
      if (selectionModel != null) {
        selectedItem = selectionModel.getSelectedItem
        if (selectedItem != null && !selectedItem.isEmpty) {
          selectedItem = chatList.getSelectionModel.getSelectedItem
        }
      }
    }
    if (selectedItem.nonEmpty) {
      selectedChat = selectedItem
      val chatParts = selectedChat.split(";")
      val groupId = chatParts(0).toInt
      val messages = DatabaseUtil.openChatRoom(groupId)
      val formattedMessages: ObservableList[String] = FXCollections.observableArrayList[String]()
      messages.forEach{message =>
        val messageParts = message.split(";")
        if (messageParts.length > 1) {
          val content = messageParts(2)
          val timestamp = messageParts(3)
          formattedMessages.add(s"[$timestamp]: $content")
        } else {
          formattedMessages.add("Corrupted message format")
        }
      }
      chatClientRef.foreach(_ ! ChatClient.ChangeCurrentChatRoom(groupId))
      chatView.setItems(formattedMessages)
    }
  }

  msgArea.onKeyPressed = (event: KeyEvent) => {
    if (event.getCode == KeyCode.ENTER) {
      if (chatList != null) {
        val selectionModel = chatList.getSelectionModel
        if (selectionModel != null) {
          val selectedItem = selectionModel.getSelectedItem
          if (selectedItem != null && !selectedItem.isEmpty) {
            val message = msgArea.text.value
            val sendResult = DatabaseUtil.sendMessage(message)
            if (sendResult) {
              val chatParts = selectedChat.split(";")
              val groupId = chatParts(0).toInt
              chatClientRef.foreach(_ ! ChatClient.SendMessage(groupId))
            }
            //            event.consume()
          }
        }
      }
      msgArea.clear()
      msgArea.text = ""
    }
  }

  def openCreateChatWindow(actionEvent: ActionEvent): Unit = {
    Client.openCreateChatWindow()
  }

  def handleQuit(actionEvent: ActionEvent): Unit = {
    DatabaseUtil.goOffline()
    Client.exitWindow()
    chatClientRef.foreach(_ ! ChatClient.GoOffline)
  }


}