package slack.api

import slack.models._
import slack.api._

import java.io.File
import java.nio.charset.StandardCharsets
import scala.concurrent.{ExecutionContext,Future}

import dispatch.{Http,Req,url,as}
import com.ning.http.multipart.FilePart
import play.api.libs.json._

object SlackApiClient {

  implicit val rtmStartStateFmt = Json.format[RtmStartState]
  implicit val accessTokenFmt = Json.format[AccessToken]
  implicit val historyChunkFmt = Json.format[HistoryChunk]
  implicit val pagingObjectFmt = Json.format[PagingObject]
  implicit val filesResponseFmt = Json.format[FilesResponse]
  implicit val fileInfoFmt = Json.format[FileInfo]
  implicit val reactionsResponseFmt = Json.format[ReactionsResponse]

  val apiBase = url("https://slack.com/api")

  def apply(token: String): SlackApiClient = {
    new SlackApiClient(token)
  }

  def exchangeOauthForToken(clientId: String, clientSecret: String, code: String, redirectUri: Option[String] = None)(implicit ec: ExecutionContext): Future[AccessToken] = {
    val params = Seq (
      ("client_id" -> clientId),
      ("client_secret" -> clientSecret),
      ("code" -> code),
      ("redirect_uri" -> redirectUri)
    )
    val res = makeApiRequest(addQueryParams(apiBase, cleanParams(params)) / "oauth.access")
    res.map(_.as[AccessToken])
  }


  private def makeApiRequest(request: Req)(implicit ec: ExecutionContext): Future[JsValue] = {
    Http(request OK as.String).map { response =>
      val parsed = Json.parse(response)
      val ok = (parsed \ "ok").as[Boolean]
      if(ok) {
        parsed
      } else {
        throw new ApiError((parsed \ "error").as[String])
      }
    }
  }

  private def extract[T](jsFuture: Future[JsValue], field: String)(implicit ec: ExecutionContext, fmt: Format[T]): Future[T] = {
    jsFuture.map(js => (js \ field).as[T])
  }

  private def addQueryParams(request: Req, queryParams: Seq[(String,String)])(implicit ec: ExecutionContext): Req = {
    var req = request
    queryParams.foreach { case (k,v) =>
      req = req.addQueryParameter(k, v)
    }
    req
  }

  private def cleanParams(params: Seq[(String,Any)]): Seq[(String,String)] = {
    var paramList = Seq[(String,String)]()
    params.foreach {
      case (k, Some(v)) => paramList :+= (k -> v.toString)
      case (k, None) => // Nothing - Filter out none
      case (k, v) => paramList :+= (k -> v.toString)
    }
    paramList
  }
}

import SlackApiClient._

class SlackApiClient(token: String) {

  val apiBaseWithToken = apiBase.addQueryParameter("token", token)


  /**************************/
  /***   Test Endpoints   ***/
  /**************************/

  def test()(implicit ec: ExecutionContext): Future[Boolean] = {
    val res = makeApiMethodRequest("api.test")
    extract[Boolean](res, "ok")
  }

  def testAuth()(implicit ec: ExecutionContext): Future[AuthIdentity] = {
    val res = makeApiMethodRequest("auth.test")
    res.map(_.as[AuthIdentity])
  }


  /***************************/
  /***  Channel Endpoints  ***/
  /***************************/

  def archiveChannel(channelId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val res = makeApiMethodRequest("channels.archive", ("channel" -> channelId))
    extract[Boolean](res, "ok")
  }

  def createChannel(name: String)(implicit ec: ExecutionContext): Future[Channel] = {
    val res = makeApiMethodRequest("channels.create", ("name" -> name))
    extract[Channel](res, "channel")
  }

  // TODO: Paging
  def getChannelHistory(channelId: String, latest: Option[Long] = None, oldest: Option[Long] = None,
      inclusive: Option[Int] = None, count: Option[Int] = None)(implicit ec: ExecutionContext): Future[HistoryChunk] = {
    val res = makeApiMethodRequest (
      "channels.history",
      ("channel" -> channelId),
      ("latest" -> latest),
      ("oldest" -> oldest),
      ("inclusive" -> inclusive),
      ("count" -> count))
    res.map(_.as[HistoryChunk])
  }

  def getChannelInfo(channelId: String)(implicit ec: ExecutionContext): Future[Channel] = {
    val res = makeApiMethodRequest("channels.info", ("channel" -> channelId))
    extract[Channel](res, "channel")
  }

  def inviteToChannel(channelId: String, userId: String)(implicit ec: ExecutionContext): Future[Channel] = {
    val res = makeApiMethodRequest("channels.invite", ("channel" -> channelId), ("user" -> userId))
    extract[Channel](res, "channel")
  }

  def joinChannel(channelId: String)(implicit ec: ExecutionContext): Future[Channel] = {
    val res = makeApiMethodRequest("channels.join", ("channel" -> channelId))
    extract[Channel](res, "channel")
  }

  def kickFromChannel(channelId: String, userId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val res = makeApiMethodRequest("channels.kick", ("channel" -> channelId), ("user" -> userId))
    extract[Boolean](res, "ok")
  }

  def listChannels(excludeArchived: Int = 0)(implicit ec: ExecutionContext): Future[Seq[Channel]] = {
    val res = makeApiMethodRequest("channels.list", ("exclude_archived" -> excludeArchived.toString))
    extract[Seq[Channel]](res, "channels")
  }

  def leaveChannel(channelId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val res = makeApiMethodRequest("channels.leave", ("channel" -> channelId))
    extract[Boolean](res, "ok")
  }

  def markChannel(channelId: String, ts: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val res = makeApiMethodRequest("channels.mark", ("channel" -> channelId), ("ts" -> ts))
    extract[Boolean](res, "ok")
  }

  // TODO: Lite Channel Object
  def renameChannel(channelId: String, name: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val res = makeApiMethodRequest("channels.rename", ("channel" -> channelId), ("name" -> name))
    extract[Boolean](res, "ok")
  }

  def setChannelPurpose(channelId: String, purpose: String)(implicit ec: ExecutionContext): Future[String] = {
    val res = makeApiMethodRequest("channels.setPurpose", ("channel" -> channelId), ("purpose" -> purpose))
    extract[String](res, "purpose")
  }

  def setChannelTopic(channelId: String, topic: String)(implicit ec: ExecutionContext): Future[String] = {
    val res = makeApiMethodRequest("channels.setTopic", ("channel" -> channelId), ("topic" -> topic))
    extract[String](res, "topic")
  }

  def unarchiveChannel(channelId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val res = makeApiMethodRequest("channels.unarchive", ("channel" -> channelId))
    extract[Boolean](res, "ok")
  }


  /**************************/
  /****  Chat Endpoints  ****/
  /**************************/

  def deleteChat(channelId: String, ts: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val res = makeApiMethodRequest("chat.delete", ("channel" -> channelId), ("ts" -> ts))
    extract[Boolean](res, "ok")
  }

  def postChatMessage(channelId: String, text: String, username: Option[String] = None, asUser: Option[Boolean] = None,
      parse: Option[String] = None, linkNames: Option[String] = None, attachments: Option[Seq[Attachment]] = None,
      unfurlLinks: Option[Boolean] = None, unfurlMedia: Option[Boolean] = None, iconUrl: Option[String] = None,
      iconEmoji: Option[String] = None)(implicit ec: ExecutionContext): Future[String] = {
    val res = makeApiMethodRequest (
      "chat.postMessage",
      ("channel" -> channelId),
      ("text" -> text),
      ("as_user" -> asUser),
      ("parse" -> parse),
      ("link_names" -> linkNames),
      ("attachments" -> attachments.map(a => Json.stringify(Json.toJson(a)))),
      ("unfurl_links" -> unfurlLinks),
      ("unfurl_media" -> unfurlMedia),
      ("icon_url" -> iconUrl),
      ("icon_emoji" -> iconEmoji))
    extract[String](res, "ts")
  }

  def updateChatMessage(channelId: String, ts: String, text: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val res = makeApiMethodRequest("chat.update", ("channel" -> channelId), ("ts" -> ts), ("text" -> text))
    extract[Boolean](res, "ok")
  }


  /***************************/
  /****  Emoji Endpoints  ****/
  /***************************/

  def listEmojis()(implicit ec: ExecutionContext): Future[Map[String,String]] = {
    val res = makeApiMethodRequest("emoji.list")
    extract[Map[String,String]](res, "emoji")
  }


  /**************************/
  /****  File Endpoints  ****/
  /**************************/

  def deleteFile(fileId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val res = makeApiMethodRequest("files.delete", ("file" -> fileId))
    extract[Boolean](res, "ok")
  }

  def getFileInfo(fileId: String, count: Option[Int] = None, page: Option[Int] = None)(implicit ec: ExecutionContext): Future[FileInfo] = {
    val res = makeApiMethodRequest("files.info", ("file" -> fileId), ("count" -> count), ("page" -> page))
    res.map(_.as[FileInfo])
  }

  def listFiles(userId: Option[String] = None, tsFrom: Option[String] = None, tsTo: Option[String] = None, types: Option[Seq[String]] = None,
      count: Option[Int] = None, page: Option[Int] = None)(implicit ec: ExecutionContext): Future[FilesResponse] = {
    val res = makeApiMethodRequest (
      "files.list",
      ("user" -> userId),
      ("ts_from" -> tsFrom),
      ("ts_to" -> tsTo),
      ("types" -> types.map(_.mkString(","))),
      ("count" -> count),
      ("page" -> page))
    res.map(_.as[FilesResponse])
  }

  def uploadFile(file: File, content: Option[String] = None, filetype: Option[String] = None, filename: Option[String] = None,
      title: Option[String] = None, initialComment: Option[String] = None, channels: Option[Seq[String]] = None)(implicit ec: ExecutionContext): Future[SlackFile] = {
    val params = Seq (
      ("content" -> content),
      ("filetype" -> filetype),
      ("filename" -> filename),
      ("title" -> title),
      ("initial_comment" -> initialComment),
      ("channels" -> channels.map(_.mkString(",")))
    )
    val multi = apiBaseWithToken.setContentType("multipart/form-data", StandardCharsets.UTF_8.toString).setHeader("Transfer-Encoding", "chunked").POST
    val withFile = multi.addBodyPart(new FilePart("file", file))
    val res = makeApiRequest(addQueryParams(withFile, cleanParams(params)))
    extract[SlackFile](res, "file")
  }


  /***************************/
  /****  Group Endpoints  ****/
  /***************************/

  def archiveGroup(channelId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val res = makeApiMethodRequest("groups.archive", ("channel" -> channelId))
    extract[Boolean](res, "ok")
  }

  def closeGroup(channelId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val res = makeApiMethodRequest("groups.close", ("channel" -> channelId))
    extract[Boolean](res, "ok")
  }

  def createGroup(name: String)(implicit ec: ExecutionContext): Future[Group] = {
    val res = makeApiMethodRequest("groups.create", ("name" -> name))
    extract[Group](res, "group")
  }

  def createChildGroup(channelId: String)(implicit ec: ExecutionContext): Future[Group] = {
    val res = makeApiMethodRequest("groups.createChild", ("channel" -> channelId))
    extract[Group](res, "group")
  }

  def getGroupHistory(channelId: String, latest: Option[Long] = None, oldest: Option[Long] = None,
      inclusive: Option[Int] = None, count: Option[Int] = None)(implicit ec: ExecutionContext): Future[HistoryChunk] = {
    val res = makeApiMethodRequest (
      "groups.history",
      ("channel" -> channelId),
      ("latest" -> latest),
      ("oldest" -> oldest),
      ("inclusive" -> inclusive),
      ("count" -> count))
    res.map(_.as[HistoryChunk])
  }

  def getGroupInfo(channelId: String)(implicit ec: ExecutionContext): Future[Group] = {
    val res = makeApiMethodRequest("groups.info", ("channel" -> channelId))
    extract[Group](res, "group")
  }

  def inviteToGroup(channelId: String, userId: String)(implicit ec: ExecutionContext): Future[Group] = {
    val res = makeApiMethodRequest("groups.invite", ("channel" -> channelId), ("user" -> userId))
    extract[Group](res, "group")
  }

  def kickFromGroup(channelId: String, userId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val res = makeApiMethodRequest("groups.kick", ("channel" -> channelId), ("user" -> userId))
    extract[Boolean](res, "ok")
  }

  def leaveGroup(channelId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val res = makeApiMethodRequest("groups.leave", ("channel" -> channelId))
    extract[Boolean](res, "ok")
  }

  def listGroups(excludeArchived: Int = 0)(implicit ec: ExecutionContext): Future[Seq[Group]] = {
    val res = makeApiMethodRequest("groups.list", ("exclude_archived" -> excludeArchived.toString))
    extract[Seq[Group]](res, "groups")
  }

  def markGroup(channelId: String, ts: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val res = makeApiMethodRequest("groups.mark", ("channel" -> channelId), ("ts" -> ts))
    extract[Boolean](res, "ok")
  }

  def openGroup(channelId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val res = makeApiMethodRequest("groups.open", ("channel" -> channelId))
    extract[Boolean](res, "ok")
  }

  // TODO: Lite Group Object
  def renameGroup(channelId: String, name: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val res = makeApiMethodRequest("groups.rename", ("channel" -> channelId), ("name" -> name))
    extract[Boolean](res, "ok")
  }

  def setGroupPurpose(channelId: String, purpose: String)(implicit ec: ExecutionContext): Future[String] = {
    val res = makeApiMethodRequest("groups.setPurpose", ("channel" -> channelId), ("purpose" -> purpose))
    extract[String](res, "purpose")
  }

  def setGroupTopic(channelId: String, topic: String)(implicit ec: ExecutionContext): Future[String] = {
    val res = makeApiMethodRequest("groups.setTopic", ("channel" -> channelId), ("topic" -> topic))
    extract[String](res, "topic")
  }

  def unarchiveGroup(channelId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val res = makeApiMethodRequest("groups.unarchive", ("channel" -> channelId))
    extract[Boolean](res, "ok")
  }

  /************************/
  /****  IM Endpoints  ****/
  /************************/

  def closeIm(channelId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val res = makeApiMethodRequest("im.close", ("channel" -> channelId))
    extract[Boolean](res, "ok")
  }

  def getImHistory(channelId: String, latest: Option[Long] = None, oldest: Option[Long] = None,
      inclusive: Option[Int] = None, count: Option[Int] = None)(implicit ec: ExecutionContext): Future[HistoryChunk] = {
    val res = makeApiMethodRequest (
      "im.history",
      ("channel" -> channelId),
      ("latest" -> latest),
      ("oldest" -> oldest),
      ("inclusive" -> inclusive),
      ("count" -> count))
    res.map(_.as[HistoryChunk])
  }

  def listIms()(implicit ec: ExecutionContext): Future[Seq[Im]] = {
    val res = makeApiMethodRequest("im.list")
    extract[Seq[Im]](res, "ims")
  }

  def markIm(channelId: String, ts: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val res = makeApiMethodRequest("im.mark", ("channel" -> channelId), ("ts" -> ts))
    extract[Boolean](res, "ok")
  }

  def openIm(userId: String)(implicit ec: ExecutionContext): Future[String] = {
    val res = makeApiMethodRequest("im.open", ("user" -> userId))
    res.map(r => (r \ "channel" \ "id").as[String])
  }


  /******************************/
  /****  Reaction Endpoints  ****/
  /******************************/

  def addReaction(emojiName: String, file: Option[String] = None, fileComment: Option[String] = None, channelId: Option[String] = None,
                    timestamp: Option[String] = None)(implicit ec: ExecutionContext): Future[Boolean] = {
    val res = makeApiMethodRequest("reactions.add", ("name" -> emojiName), ("file" -> file), ("file_comment" -> fileComment),
                                        ("channel" -> channelId), ("timestamp" -> timestamp))
    extract[Boolean](res, "ok")
  }

  def addReactionToMessage(emojiName: String, channelId: String, timestamp: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    addReaction(emojiName = emojiName, channelId = Some(channelId), timestamp = Some(timestamp))
  }

  def getReactions(file: Option[String] = None, fileComment: Option[String] = None, channelId: Option[String] = None,
                    timestamp: Option[String] = None, full: Option[Boolean] = None)(implicit ec: ExecutionContext): Future[Seq[Reaction]] = {
    val res = makeApiMethodRequest("reactions.get", ("file" -> file), ("file_comment" -> fileComment), ("channel" -> channelId),
                                            ("timestamp" -> timestamp), ("full" -> full))
    res.map(r => (r \\ "reactions").headOption.map(_.as[Seq[Reaction]]).getOrElse(Seq[Reaction]()))
  }

  def getReactionsForMessage(channelId: String, timestamp: String, full: Option[Boolean] = None)(implicit ec: ExecutionContext): Future[Seq[Reaction]] = {
    getReactions(channelId = Some(channelId), timestamp = Some(timestamp), full = full)
  }

  def listReactionsForUser(userId: Option[String], full: Boolean = false, count: Option[Int] = None, page: Option[Int] = None)(implicit ec: ExecutionContext): Future[ReactionsResponse] = {
    val res = makeApiMethodRequest("reations.list", ("user" -> userId), ("full" -> full), ("count" -> count), ("page" -> page))
    res.map(_.as[ReactionsResponse])
  }

  def removeReaction(emojiName: String, file: Option[String] = None, fileComment: Option[String] = None, channelId: Option[String] = None,
                    timestamp: Option[String] = None)(implicit ec: ExecutionContext): Future[Boolean] = {
    val res = makeApiMethodRequest("reactions.remove", ("name" -> emojiName), ("file" -> file), ("file_comment" -> fileComment),
                                        ("channel" -> channelId), ("timestamp" -> timestamp))
    extract[Boolean](res, "ok")
  }

  def removeReactionFromMessage(emojiName: String, channelId: String, timestamp: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    removeReaction(emojiName = emojiName, channelId = Some(channelId), timestamp = Some(timestamp))
  }

  /*************************/
  /****  RTM Endpoints  ****/
  /*************************/

  def startRealTimeMessageSession()(implicit ec: ExecutionContext): Future[RtmStartState] = {
    val res = makeApiMethodRequest("rtm.start")
    res.map(_.as[RtmStartState])
  }


  /****************************/
  /****  Search Endpoints  ****/
  /****************************/

  // TODO: Return proper search results (not JsValue)
  def searchAll(query: String, sort: Option[String] = None, sortDir: Option[String] = None, highlight: Option[String] = None,
      count: Option[Int] = None, page: Option[Int] = None)(implicit ec: ExecutionContext): Future[JsValue] = {
    makeApiMethodRequest (
      "search.all",
      ("query" -> query),
      ("sort" -> sort),
      ("sortDir" -> sortDir),
      ("highlight" -> highlight),
      ("count" -> count),
      ("page" -> page))
  }

  // TODO: Return proper search results (not JsValue)
  def searchFiles(query: String, sort: Option[String] = None, sortDir: Option[String] = None, highlight: Option[String] = None,
      count: Option[Int] = None, page: Option[Int] = None)(implicit ec: ExecutionContext): Future[JsValue] = {
    makeApiMethodRequest (
      "search.files",
      ("query" -> query),
      ("sort" -> sort),
      ("sortDir" -> sortDir),
      ("highlight" -> highlight),
      ("count" -> count),
      ("page" -> page))
  }

  // TODO: Return proper search results (not JsValue)
  def searchMessages(query: String, sort: Option[String] = None, sortDir: Option[String] = None, highlight: Option[String] = None,
      count: Option[Int] = None, page: Option[Int] = None)(implicit ec: ExecutionContext): Future[JsValue] = {
    makeApiMethodRequest (
      "search.messages",
      ("query" -> query),
      ("sort" -> sort),
      ("sortDir" -> sortDir),
      ("highlight" -> highlight),
      ("count" -> count),
      ("page" -> page))
  }

  /***************************/
  /****  Stars Endpoints  ****/
  /***************************/

  // TODO: Return proper star items (not JsValue)
  def listStars(userId: Option[String] = None, count: Option[Int] = None, page: Option[Int] = None)(implicit ec: ExecutionContext): Future[JsValue] = {
    makeApiMethodRequest("start.list", ("user" -> userId), ("count" -> count), ("page" -> page))
  }


  /**************************/
  /****  Team Endpoints  ****/
  /**************************/

  // TODO: Pares actual result type: https://api.slack.com/methods/team.accessLogs
  def getTeamAccessLogs(count: Option[Int], page: Option[Int])(implicit ec: ExecutionContext): Future[JsValue] = {
    makeApiMethodRequest("team.accessLogs", ("count" -> count), ("page" -> page))
  }

  // TODO: Parse actual value type: https://api.slack.com/methods/team.info
  def getTeamInfo()(implicit ec: ExecutionContext): Future[JsValue] = {
    makeApiMethodRequest("team.info")
  }


  /**************************/
  /****  User Endpoints  ****/
  /**************************/

  // TODO: Full payload for authed user: https://api.slack.com/methods/users.getPresence
  def getUserPresence(userId: String)(implicit ec: ExecutionContext): Future[String] = {
    val res = makeApiMethodRequest("users.getPresence", ("user" -> userId))
    extract[String](res, "presence")
  }

  def getUserInfo(userId: String)(implicit ec: ExecutionContext): Future[User] = {
    val res = makeApiMethodRequest("users.getInfo", ("user" -> userId))
    extract[User](res, "user")
  }

  def listUsers()(implicit ec: ExecutionContext): Future[Seq[User]] = {
    val res = makeApiMethodRequest("users.list")
    extract[Seq[User]](res, "members")
  }

  def setUserActive(userId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val res = makeApiMethodRequest("users.setActive", ("user" -> userId))
    extract[Boolean](res, "ok")
  }

  def setUserPresence(presence: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val res = makeApiMethodRequest("users.setPresence", "presence" -> presence)
    extract[Boolean](res, "ok")
  }


  /*****************************/
  /****  Private Functions  ****/
  /*****************************/

  private def makeApiMethodRequest(apiMethod: String, queryParams: (String,Any)*)(implicit ec: ExecutionContext): Future[JsValue] = {
    var req = apiBaseWithToken / apiMethod
    makeApiRequest(addQueryParams(req, cleanParams(queryParams)))
  }
}

case class ApiError(code: String) extends Exception(code)

case class HistoryChunk (
  latest: Long,
  messages: Seq[JsValue],
  has_more: Boolean
)

case class FileInfo (
  file: SlackFile,
  comments: Seq[SlackComment],
  paging: PagingObject
)

case class FilesResponse (
  files: Seq[SlackFile],
  paging: PagingObject
)

case class ReactionsResponse (
  items: Seq[JsValue], // TODO: Parse out each object type w/ reactions
  paging: PagingObject
)

case class PagingObject (
  count: Int,
  total: Int,
  page: Int,
  pages: Int
)

case class AccessToken (
  access_token: String,
  scope: String
)

case class RtmStartState (
  url: String,
  self: User,
  team: Team,
  users: Seq[User],
  channels: Seq[Channel],
  groups: Seq[Group],
  ims: Seq[Im],
  bots: Seq[JsValue]
)