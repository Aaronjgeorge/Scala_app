package com.hep88

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import scalafx.application.Platform
import javafx.collections.{FXCollections, ObservableSet, SetChangeListener}
import com.hep88.ChatServer.UpdateUserMessages



object UserSession {
  var currentUserName: Option[String] = None
}

object ChatClient {
  // Chat client protocol
  sealed trait Command
  case class LoginRequest(username: String, password: String, callback: (Boolean, Int) => Unit) extends Command
  case class LoginResponse(result: ChatServer.LoginResult) extends Command
  case class RegisterRequest(username: String, password: String) extends Command
  case class RegisterResponse(result: ChatServer.RegistrationResult) extends Command
  // Define a message for search request
  case class SearchRequest(query: String, callback: Seq[String] => Unit) extends Command

  // Define a message for search response
  case class SearchResponse(result: Seq[String]) extends Command
  val ClientKey: ServiceKey[Command] = ServiceKey("chatClient")

  case class AddFriendRequest(friendUsername: String, callback: Boolean => Unit) extends Command
  case class AddFriendResponse(result: Boolean) extends Command
  case class FetchFriendsRequest(callback: Seq[String] => Unit) extends Command
  case class FetchFriendsResponse(friendsList: Seq[String]) extends Command
  case object UpdateOnlineUserList extends Command
  case class SetUserId(userId: Int) extends Command
  case class ServerRefRequest(callback: (Option[ActorRef[ChatServer.Command]]) => Unit) extends Command
  case class ServerRefResponse(result: ChatServer.ServerRefResult) extends Command
  case class ChangeCurrentChatRoom(chatRoom: Int) extends Command
  case class SendMessage(groupId: Int) extends Command
  case class UpdateChatView(groupId: Int) extends Command
  case object GoOffline extends Command
  case class CreateGroupChatResponse(result: Boolean) extends Command
  case object start extends Command

  private case class ListingResponse(listing: Receptionist.Listing) extends Command
  private var remoteOpt: Option[ActorRef[ChatServer.Command]] = None
  private var currentCallback: Option[(Boolean, Int) => Unit] = None
  private var searchCallback: Option[Seq[String] => Unit] = None
  private var serverRefCallback: Option[Option[ActorRef[ChatServer.Command]] => Unit] = None
  private var addFriendCallback: Option[Boolean => Unit] = None

  var userId: Int = _
  var currentChatRoomId: Int = -1

  // Find the chat server
  final case object FindTheServer extends Command

  def apply(): Behavior[ChatClient.Command] =
    Behaviors.setup { context =>
      context.system.receptionist ! Receptionist.Register(ClientKey, context.self)
      // Create a Receptionist Listing adapter
      val listingAdapter: ActorRef[Receptionist.Listing] =
        context.messageAdapter(listing => ListingResponse(listing))

      // Subscribe to Receptionist events related to ChatServer
      context.system.receptionist ! Receptionist.Subscribe(ChatServer.ServerKey, listingAdapter)

      // Create a login response adapter
      val loginResponseAdapter: ActorRef[ChatServer.LoginResult] =
        context.messageAdapter { rsp =>
          rsp match {
            case ChatServer.LoginSuccess(validatedUserId) =>
              currentCallback.foreach(_(true, validatedUserId))
              currentCallback = None
            case ChatServer.LoginFailure =>
              currentCallback.foreach(_(false, 0))
              currentCallback = None
          }
          LoginResponse(rsp)
        }
      val searchResponseAdapter: ActorRef[Seq[String]] =
        context.messageAdapter(searchResults => SearchResponse(searchResults))
      val addFriendResponseAdapter: ActorRef[Boolean] =
        context.messageAdapter(result => AddFriendResponse(result))

      val registrationResponseAdapter: ActorRef[ChatServer.RegistrationResult] =
        context.messageAdapter(rsp => RegisterResponse(rsp))

      val fetchFriendsResponseAdapter: ActorRef[Seq[String]] =
        context.messageAdapter(friendsList => FetchFriendsResponse(friendsList))
      val serverRefResponseAdapter: ActorRef[ChatServer.ServerRefResult] =
        context.messageAdapter { rsp =>
          rsp match {
            case ChatServer.ServerRefSuccess =>
              serverRefCallback.foreach(_(remoteOpt))
              serverRefCallback = None
            case ChatServer.ServerRefFailure =>
              serverRefCallback.foreach(_(None))
              serverRefCallback = None
          }
          ServerRefResponse(rsp)
        }


      Behaviors.receiveMessage {
        case ChatClient.start =>
          context.self ! FindTheServer
          Behaviors.same

        case FindTheServer =>
          context.system.receptionist ! Receptionist.Find(ChatServer.ServerKey, listingAdapter)
          Behaviors.same

        case ListingResponse(ChatServer.ServerKey.Listing(listings)) =>
          remoteOpt = listings.headOption
          println(s"Received server listing: $listings") // Add this for debugging
          Behaviors.same


        case LoginRequest(username, password, callback) =>
          println(s"Login request initiated for user: $username")
          UserSession.currentUserName = Some(username) // Set the user ID here
          remoteOpt.foreach { remote =>
            remote ! ChatServer.LoginUser(username, password, loginResponseAdapter)
          }
          currentCallback = Some(callback)
          Behaviors.same


        case LoginResponse(result) =>
          result match {
            case ChatServer.LoginSuccess(validatedUserId) =>
              Platform.runLater {
                println("User has been logged in!")
              }
            case ChatServer.LoginFailure =>
              Platform.runLater {
                // Handle login failure
              }
          }
          Behaviors.same

        case RegisterRequest(username, password) =>
          remoteOpt.foreach { remote =>
            remote ! ChatServer.RegisterUser(username, password, registrationResponseAdapter)
          }
          Behaviors.same

        case RegisterResponse(result) =>
          result match {
            case ChatServer.RegistrationSuccess =>
              Platform.runLater {
                // Handle successful registration
              }
            case ChatServer.RegistrationFailure =>
              Platform.runLater {
                // Handle registration failure
              }
          }
          Behaviors.same

        case SearchRequest(query, callback) =>
          remoteOpt.foreach { remote =>
            // Send search request to ChatServer
            remote ! ChatServer.SearchUser(query, searchResponseAdapter)
          }
          searchCallback = Some(callback)
          Behaviors.same


        case SearchResponse(result) =>
          searchCallback.foreach(_(result))
          searchCallback = None
          Behaviors.same

        case AddFriendRequest(friendUsername, callback) =>
          val userName = UserSession.currentUserName.get // Get the current user's username
          remoteOpt.foreach { remote =>
            remote ! ChatServer.AddFriend(userName, friendUsername, addFriendResponseAdapter)
          }
          addFriendCallback = Some(callback)
          Behaviors.same

        case AddFriendResponse(result) =>
          addFriendCallback.foreach(_(result))
          addFriendCallback = None
          Behaviors.same




        case FetchFriendsRequest(callback) =>
          UserSession.currentUserName match {
            case Some(userName) =>
              remoteOpt.foreach { remote =>
                remote ! ChatServer.FetchFriends(userName, fetchFriendsResponseAdapter)
              }
            case None =>
            // Handle case when user is not logged in
          }
          searchCallback = Some(callback)
          Behaviors.same

        case FetchFriendsResponse(friendsList) =>
          searchCallback.foreach(_(friendsList))
          searchCallback = None
          Behaviors.same

        case UpdateOnlineUserList =>
          Client.populateOnlineFriendsList(userId)
          Behaviors.same

        case SetUserId(validatedUserId) =>
          userId = validatedUserId
          Behaviors.same

        case ServerRefRequest(callback) =>
          remoteOpt.foreach{remote =>
            remote ! ChatServer.GetServerRef(serverRefResponseAdapter)
          }
          serverRefCallback = Some(callback)
          Behaviors.same

        case ServerRefResponse(result) =>
          result match {
            case ChatServer.ServerRefSuccess =>
              Platform.runLater {
                println("Server ref get!")
              }
            case ChatServer.ServerRefFailure =>
              Platform.runLater {
                println("No server ref")
              }
          }
          Behaviors.same

        case ChangeCurrentChatRoom(chatRoom) =>
          currentChatRoomId = chatRoom
          Behaviors.same

        case SendMessage(groupId) =>
          remoteOpt.foreach{remote =>
            remote ! ChatServer.UpdateUserMessages(groupId)
          }
          Behaviors.same

        case UpdateChatView(groupId) =>
          Client.updateChat(groupId)
          Behaviors.same

        case GoOffline =>
          print("Going offline")
          remoteOpt.foreach { remote =>
            remote ! ChatServer.FindClients
          }
          Behaviors.same
      }
      }
}