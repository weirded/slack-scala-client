package slack.models

// TODO: 22 field limit :(
case class SlackFile (
  id: String,
  created: Long,
  timestamp: Long,
  name: Option[String],
  title: String,
  mimetype: String,
  filetype: String,
  pretty_type: String,
  user: String,
  mode: String,
  editable: Boolean,
  is_external: Boolean,
  external_type: String,
  size: Long,
  url: String,
  url_download: Option[String],
  url_private: Option[String],
  url_private_download: Option[String]
  //thumb_64: Option[String],
  //thumb_80: Option[String],
  //thumb_360: Option[String],
  //thumb_360_gif: Option[String],
  //thumb_360_w: Option[String],
  //thumb_360_h: Option[String],
  //permalink: String,
  //edit_link: Option[String],
  //preview: Option[String],
  //preview_highlight: Option[String],
  //lines: Option[Int],
  //lines_more: Option[Int],
  //is_public: Boolean,
  //public_url_shared: Boolean,
  //channels: Seq[String],
  //groups: Option[Seq[String]],
  //initial_comment: Option[JsValue], // TODO: SlackComment?
  //num_stars: Option[Int],
  //is_starred: Option[Boolean]
)