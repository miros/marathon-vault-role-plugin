package io.funbox.marathon.plugin.vault

import play.api.libs.json.{JsObject, JsValue}

class Json(json: JsObject) {

  def getStr(path: String): String = getValue(path).as[String]

  def getInt(path: String): Int = getValue(path).as[Int]

  def get(path: String): Json = new Json(getValue(path).as[JsObject])

  // TODO raise meaningfull error
  private def getValue(path: String) = (json \ path).get
}