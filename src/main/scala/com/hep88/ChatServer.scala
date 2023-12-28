package com.hep88

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import com.hep88.DatabaseUtil
import akka.actor.typed.ActorSystem // Import ActorSystem

object ChatServer {
  sealed trait Command
  case class RegisterUser(username: String, password: String, replyTo: ActorRef[RegistrationResult]) extends Command
  case class LoginUser(username: String, password: String, replyTo: ActorRef[LoginResult]) extends Command

  sealed trait RegistrationResult
  case object RegistrationSuccess extends RegistrationResult
  case object RegistrationFailure extends RegistrationResult

  sealed trait LoginResult
  case object LoginSuccess extends LoginResult
  case object LoginFailure extends LoginResult
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

  def apply(): Behavior[ChatServer.Command] =
    Behaviors.setup { context =>
      context.system.receptionist ! Receptionist.Register(ServerKey, context.self)

      Behaviors.receiveMessage {
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
          if (DatabaseUtil.validateUser(username, password)) {
            replyTo ! LoginSuccess
          } else {
            replyTo ! LoginFailure
          }
          Behaviors.same
      }
    }
}

object Server extends App {
  // Start the ChatServer
//  ChatServer.testRegisterUser()
  val greeterMain: ActorSystem[ChatServer.Command] = ActorSystem(ChatServer(), "ChatSystem")
  print("Server has started!")
}