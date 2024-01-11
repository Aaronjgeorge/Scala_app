package com.hep88

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import com.hep88.DatabaseUtil
import akka.actor.typed.ActorSystem

object ChatServer {
  sealed trait Command
  case class test(client: ActorRef[ChatClient.Command]) extends Command
  case class UpdateUserMessages(groupId: Int) extends Command
  case class UpdateTotalOnlineUserList(userId: Int) extends Command
  case class SetServerRef(serverRef: ActorRef[Command]) extends Command
  case class RegisterUser(username: String, password: String, replyTo: ActorRef[RegistrationResult]) extends Command
  case class LoginUser(username: String, password: String, replyTo: ActorRef[LoginResult]) extends Command
  case class SearchUser(query: String, replyTo: ActorRef[Seq[String]]) extends Command
  case class AddFriend(currentUserName: String, friendUsername: String, replyTo: ActorRef[Boolean]) extends Command
  case class FetchFriends(userName: String, replyTo: ActorRef[Seq[String]]) extends Command
  sealed trait RegistrationResult
  case object RegistrationSuccess extends RegistrationResult
  case object RegistrationFailure extends RegistrationResult

  sealed trait LoginResult
  case class LoginSuccess(validatedUserId: Int) extends LoginResult
  case object LoginFailure extends LoginResult
  sealed trait AddFriendResult
  case object AddFriendSuccess extends AddFriendResult
  case object AddFriendFailure extends AddFriendResult
  case class GetServerRef(replyTo: ActorRef[ServerRefResult]) extends Command
  sealed trait ServerRefResult
  case object ServerRefSuccess extends ServerRefResult
  case object ServerRefFailure extends ServerRefResult

  final case object FindClients extends Command
  private case class ListingResponse(listing: Receptionist.Listing) extends Command
  def testRegisterUser(): Unit = {
    val testUsername = "testUser"
    val testPassword = "testPassword"

    if (DatabaseUtil.createUser(testUsername, testPassword)) {
      println("Test user registered successfully.")
    } else {
      println("Failed to register test user.")
    }
  }
  val ServerKey: ServiceKey[ChatServer.Command] = ServiceKey("chatServer")
  var serverRef: Option[ActorRef[Command]] = None
  var onlineUsers: Set[ActorRef[ChatClient.Command]] = _

  def apply(): Behavior[ChatServer.Command] =
    Behaviors.setup { context =>
      context.system.receptionist ! Receptionist.Register(ServerKey, context.self)
      val listingAdapter: ActorRef[Receptionist.Listing] =
        context.messageAdapter(listing => ListingResponse(listing))
      Behaviors.receiveMessage {
        case FindClients =>
          context.system.receptionist ! Receptionist.Find(ChatClient.ClientKey, listingAdapter)
          Behaviors.same

        case ListingResponse(ChatClient.ClientKey.Listing(listings)) =>
          onlineUsers = listings
          onlineUsers.foreach{userRef =>
            userRef ! ChatClient.UpdateOnlineUserList
          }
          println(s"Received client server listing: $onlineUsers")
          Behaviors.same
        case RegisterUser(username, password, replyTo) =>
          if (!DatabaseUtil.userExists(username)) {
            if (DatabaseUtil.createUser(username, password)) {
              replyTo ! RegistrationSuccess
            } else {
              replyTo ! RegistrationFailure
            }
          } else {
            replyTo ! RegistrationFailure
          }
          Behaviors.same

        case LoginUser(username, password, replyTo) =>
          println(s"Login attempt for user: $username")
          val result: (Boolean, Int) = DatabaseUtil.validateUser(username, password)
          val loginResult: Boolean = result._1
          val validatedUserId: Int = result._2

          if (loginResult) {
            replyTo ! LoginSuccess(validatedUserId)
          } else {
            replyTo ! LoginFailure
          }
          Behaviors.same

        case SearchUser(query, replyTo) =>
          // Perform the database query and get the results
          val searchResults = DatabaseUtil.searchUser(query)
          // Send the results back to the adapter in ChatClient
          replyTo ! searchResults
          Behaviors.same

        case AddFriend(currentUserName, friendUsername, replyTo) =>
          val result = DatabaseUtil.addFriend(currentUserName, friendUsername)
          replyTo ! result  // result is a Boolean
          Behaviors.same

//        case FetchFriends(userName, replyTo) =>
//          val friendsList = DatabaseUtil.fetchFriends(userName)
//          replyTo ! friendsList
//          Behaviors.same

        case SetServerRef(greeterMain) =>
          serverRef = Some(greeterMain)
          Behaviors.same
        case GetServerRef(replyTo) =>
          if (serverRef.isDefined) {
            replyTo ! ServerRefSuccess
          } else {
            replyTo ! ServerRefFailure
          }
          Behaviors.same

        case UpdateUserMessages(groupId) =>
          onlineUsers.foreach{userRef =>
            userRef ! ChatClient.UpdateChatView(groupId)
          }
          Behaviors.same
      }
      }
}

object Server extends App {
  // Start the ChatServer
//  ChatServer.testRegisterUser()
  val greeterMain: ActorSystem[ChatServer.Command] = ActorSystem(ChatServer(), "ChatSystem")
  greeterMain ! ChatServer.SetServerRef(greeterMain)
  print("Server has started!")
}