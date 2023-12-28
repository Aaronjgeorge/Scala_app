package com.hep88

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import scalafx.application.Platform

object ChatClient {
  // Chat client protocol
  sealed trait Command
  case class LoginRequest(username: String, password: String, callback: Boolean => Unit) extends Command
  case class LoginResponse(result: ChatServer.LoginResult) extends Command
  case class RegisterRequest(username: String, password: String) extends Command
  case class RegisterResponse(result: ChatServer.RegistrationResult) extends Command
  case object start extends Command
  private case class ListingResponse(listing: Receptionist.Listing) extends Command
  private var remoteOpt: Option[ActorRef[ChatServer.Command]] = None
  private var currentCallback: Option[Boolean => Unit] = None

  // Find the chat server
  final case object FindTheServer extends Command

  def apply(): Behavior[ChatClient.Command] =
    Behaviors.setup { context =>
      // Create a Receptionist Listing adapter
      val listingAdapter: ActorRef[Receptionist.Listing] =
        context.messageAdapter(listing => ListingResponse(listing))

      // Subscribe to Receptionist events related to ChatServer
      context.system.receptionist ! Receptionist.Subscribe(ChatServer.ServerKey, listingAdapter)

      // Create a login response adapter
      val loginResponseAdapter: ActorRef[ChatServer.LoginResult] =
        context.messageAdapter { rsp =>
          rsp match {
            case ChatServer.LoginSuccess =>
              currentCallback.foreach(_(true))
              currentCallback = None
            case ChatServer.LoginFailure =>
              currentCallback.foreach(_(false))
              currentCallback = None
          }
          LoginResponse(rsp)
        }

      val registrationResponseAdapter: ActorRef[ChatServer.RegistrationResult] =
        context.messageAdapter(rsp => RegisterResponse(rsp))
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
          remoteOpt.foreach { remote =>
            remote ! ChatServer.LoginUser(username, password, loginResponseAdapter)
          }
          currentCallback = Some(callback)
          Behaviors.same


        case LoginResponse(result) =>
          result match {
            case ChatServer.LoginSuccess =>
              Platform.runLater {
                print("User has been logged in!")
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
      }
    }
}