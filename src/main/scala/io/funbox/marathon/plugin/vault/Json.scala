package io.funbox.marathon.plugin.vault

import play.api.libs.json._

class Json(json: JsObject) {

  def tryStr(path: String): String = getValue(path).as[String]

  def tryInt(path: String): Int = getValue(path).as[Int]

  def getStr(path: String): String = requireValue(path).as[String]

  def getInt(path: String): Int = requireValue(path).as[Int]

  def get(path: String): Json = {
    val value = getValue(path)

    if (value == null) {
      return null
    }

    value.validate[JsObject] match {
      case JsSuccess(obj, _) => new Json(obj)
      case JsError(_) => null
    }
  }

  private def getValue(path: String) = {
    json \ path match {
      case JsDefined(value) => value
      case JsUndefined() => null
    }
  }

  private def requireValue(path: String) = {
    getValue(path) match {
      case null => throw new RuntimeException(s"no value for config key:$path")
      case value => value
    }
  }

}