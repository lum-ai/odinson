package controllers

import javax.inject._
import play.api.mvc._
import akka.actor._

@Singleton
class OpenApiController @Inject() (system: ActorSystem, cc: ControllerComponents)
    extends AbstractController(cc) {

  def openAPI() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.api())
  }

}
