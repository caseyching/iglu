/*
* Copyright (c) 2014 Snowplow Analytics Ltd. All rights reserved.
*
* This program is licensed to you under the Apache License Version 2.0, and
* you may not use this file except in compliance with the Apache License
* Version 2.0.  You may obtain a copy of the Apache License Version 2.0 at
* http://www.apache.org/licenses/LICENSE-2.0.
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the Apache License Version 2.0 is distributed on an "AS
* IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
* implied.  See the Apache License Version 2.0 for the specific language
* governing permissions and limitations there under.
*/
package com.snowplowanalytics.iglu.server
package test.model

// This project
import model.SchemaDAO
import util.IgluPostgresDriver.simple._
import util.Config

// Slick
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.{ StaticQuery => Q }

// Specs2
import org.specs2.mutable.Specification

// Spray
import spray.http.StatusCodes._

class SchemaSpec extends Specification with SetupAndDestroy {

  val schema = new SchemaDAO(database)

  val tableName = "schemas"
  val owner = "com.snowplowanalytics"
  val otherOwner = "com.unit"
  val faultyOwner = "com.benfradet"
  val permission = "write"
  val isPublic = false
  val vendor = "com.snowplowanalytics.snowplow"
  val vendors = List(vendor)
  val otherVendor = "com.unittest"
  val otherVendors = List(otherVendor)
  val faultyVendor = "com.snowplow"
  val faultyVendors = List(faultyVendor)
  val name = "ad_click"
  val names = List(name)
  val otherName = "ad_click2"
  val otherNames = List(otherName)
  val faultyName = "ad_click3"
  val faultyNames = List(faultyName)
  val format = "jsonschema"
  val notSupportedFormat = "notSupportedFormat"
  val formats = List(format)
  val version = "1-0-0"
  val versions = List(version)

  val invalidSchema = """{ "some" : "json" }"""
  val innerSchema = """"some" : "json""""
  val validSchema = 
  """{
    "self": {
      "vendor": "com.snowplowanalytics.snowplow",
      "name": "ad_click",
      "format": "jsonschema",
      "version": "1-0-0"
    }
  }"""
  val notJson = "not json"

  val validInstance = """{ "targetUrl": "somestr" }"""

  sequential

  "SchemaDAO" should {

    "for createTable" should {

      "create the schemas table" in {
        schema.createTable
        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from pg_catalog.pg_tables
            where tablename = '${tableName}';""").first === 1
        }
      }
    }

    "for add" should {

      "add a private schema properly" in {
        val (status, res) = schema.add(vendor, name, format, version,
          invalidSchema, owner, permission, isPublic)
        status === Created
        res must contain("Schema added successfully") and contain(vendor)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${name}' and
              format = '${format}' and
              version = '${version}' and
              ispublic = false;""").first === 1
        }
      }

      "add a public schema properly" in {
        val (status, res) = schema.add(otherVendor, otherName, format, version,
          invalidSchema, otherOwner, permission, !isPublic)
        status === Created
        res must contain("Schema added successfully") and contain(otherVendor)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${otherVendor}' and
              name = '${otherName}' and
              format = '${format}' and
              version = '${version}' and
              ispublic = true;""").first === 1
        }
      }

      "not add a schema if it already exists" in {
        val (status, res) = schema.add(vendor, name, format, version,
          invalidSchema, owner, permission, isPublic)
        status === Unauthorized
        res must contain("This schema already exists")

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${name}' and
              format = '${format}' and
              version = '${version}';""").first === 1
        }
      }
    }

    "for get" should {

      "retrieve a schema properly if it is private" in {
        val (status, res) = schema.get(vendors, names, formats, versions, owner)
        status === OK
        res must contain(innerSchema)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${name}' and
              format = '${format}' and
              version = '${version}' and
              ispublic = false;""").first === 1
        }
      }

      "retrieve a schema properly if it is public" in {
        val (status, res) =
          schema.get(otherVendors, otherNames, formats, versions, faultyOwner)
        status === OK
        res must contain(innerSchema)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${otherVendor}' and
              name = '${otherName}' and
              format = '${format}' and
              version = '${version}' and
              ispublic = true;""").first === 1
        }
      }

      """return a 401 if the owner is not a prefix of the vendor and the schema
      is private""" in {
        val (status, res) =
          schema.get(vendors, names, formats, versions, faultyOwner)
        status === Unauthorized
        res must contain("You do not have sufficient privileges")
      }

      "return a 404 if the schema is not in the db" in {
        val (status, res) =
         schema.get(vendors, faultyNames, formats, versions, owner)
        status === NotFound
        res must contain("There are no schemas available here")

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${faultyName}' and
              format = '${format}' and
              version = '${version}';""").first === 0
        }
      }
    }

    "for getMetadata" should {

      "retrieve metadata about a schema properly if it is private" in {
        val (status, res) =
          schema.getMetadata(vendors, names, formats, versions, owner)
        status === OK
        res must contain(vendor) and contain(name) and contain(format) and
          contain(version)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${name}' and
              format = '${format}' and
              version = '${version}' and
              ispublic = false;""").first === 1
        }
      }

      "retrieve metadata about a schema properly if it is public" in {
        val (status, res) = schema.getMetadata(otherVendors, otherNames,
          formats, versions, faultyOwner)
        status === OK
        res must contain(vendor) and contain(otherName) and contain(format) and
          contain(version)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${otherVendor}' and
              name = '${otherName}' and
              format = '${format}' and
              version = '${version}' and
              ispublic = true;""").first === 1
        }
      }

      """return a 401 if the owner is not a prefix of the vendor and the schema
      is private""" in {
        val (status, res) =
          schema.getMetadata(vendors, names, formats, versions, faultyOwner)
        status === Unauthorized
        res must contain("You do not have sufficient privileges")
      }

      "return a 404 if the schema is not in the db" in {
        val (status, res) =
          schema.getMetadata(vendors, faultyNames, formats, versions, owner)
        status === NotFound
        res must contain("There are no schemas available here")

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${faultyName}' and
              format = '${format}' and
              version = '${version}';""").first === 0
        }
      }
    }

    "for getPublicSchemas" should {

      "retrieve every public schema available" in {
        val (status, res) = schema.getPublicSchemas
        status === OK
        res must contain(innerSchema)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where ispublic = true;""").first === 1
        }
      }
    }

    "for getPublicMetadata" should {
      
      "retrieve metadata about every public schema available" in {
        val (status, res) = schema.getPublicMetadata
        status === OK
        res must contain(otherVendor) and contain(otherName) and
          contain(format) and contain(version)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where ispublic = true;""").first === 1
        }
      }
    }

    "for getFromFormat" should {

      "retrieve schemas properly if they are public" in {
        val (status, res) = schema.getFromFormat(vendors, names, formats, owner)
        status === OK
        res must contain(innerSchema)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${name}' and
              format = '${format}' and
              ispublic = false;""").first === 1
        }
      }

      "retrieve schemas properly if they are public" in {
        val (status, res) =
          schema.getFromFormat(otherVendors, otherNames, formats, faultyOwner)
        status === OK
        res must contain(innerSchema)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${otherVendor}' and
              name = '${otherName}' and
              format = '${format}' and
              ispublic = true;""").first === 1
        }
      }

      """return a 401 if the owner is not a prefix of the vendor and the schema
      is private""" in {
        val (status, res) =
          schema.getFromFormat(vendors, names, formats, faultyOwner)
        status === Unauthorized
        res must contain("You do not have sufficient privileges")
      }

      "return a 404 if there are no schemas matching the query" in {
        val (status, res) =
          schema.getFromFormat(vendors, faultyNames, formats, owner)
        status === NotFound
        res must contain("There are no schemas for this vendor, name, format")

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${faultyName}' and
              format = '${format}';""").first === 0
        }
      }
    }

    "for getMetadataFromFormat" should {

      "retrieve schemas properly if they are private" in {
        val (status, res) =
          schema.getMetadataFromFormat(vendors, names, formats, owner)
        status === OK
        res must contain(vendor) and contain(name) and contain(format)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${name}' and
              format = '${format}' and
              ispublic = false;""").first === 1
        }
      }

      "retrieve schemas properly if they are public" in {
        val (status, res) = schema.getMetadataFromFormat(otherVendors,
          otherNames, formats, faultyOwner)
        status === OK
        res must contain(vendor) and contain(otherName) and contain(format)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${otherVendor}' and
              name = '${otherName}' and
              format = '${format}' and
              ispublic = true;""").first === 1
        }
      }

      """return a 401 if the owner is not a prefix of the vendor and the schema
      is private""" in {
        val (status, res) =
          schema.getMetadataFromFormat(vendors, names, formats, faultyOwner)
        status === Unauthorized
        res must contain("You do not have sufficient privileges")
      }

      "return a 404 if there are no schemas matching the query" in {
        val (status, res) =
          schema.getMetadataFromFormat(vendors, faultyNames, formats, owner)
        status === NotFound
        res must contain ("There are no schemas for this vendor, name, format")

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${faultyName}' and
              format = '${format}';""").first === 0
        }
      }
    }

    "for getFromName" should {

      "retrieve schemas properly if they are private" in {
        val (status, res) = schema.getFromName(vendors, names, owner)
        status === OK
        res must contain(innerSchema)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${name}' and
              ispublic = false;""").first === 1
        }
      }

      "retrieve schemas properly if they are public" in {
        val (status, res) =
          schema.getFromName(otherVendors, otherNames, faultyOwner)
        status === OK
        res must contain(innerSchema)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${otherVendor}' and
              name = '${otherName}' and
              ispublic = true;""").first === 1
        }
      }

      """return a 401 if the owner is not a prefix of the vendor and the schema
      is private""" in {
        val (status, res) =
          schema.getFromName(vendors, names, faultyOwner)
        status === Unauthorized
        res must contain("You do not have sufficient privileges")
      }

      "return a 404 if there are no schemas matching the query" in {
        val (status, res) = schema.getFromName(vendors, faultyNames, owner)
        status === NotFound
        res must contain("There are no schemas for this vendor, name")

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${faultyName}';""").first === 0
        }
      }
    }

    "for getMetadataFromName" should {

      "retrieve schemas properly if they are private" in {
        val (status, res) = schema.getMetadataFromName(vendors, names, owner)
        status === OK
        res must contain(vendor) and contain(name)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${name}' and
              ispublic = false;""").first === 1
        }
      }

      "retrieve schemas properly if they are public" in {
        val (status, res) =
          schema.getMetadataFromName(otherVendors, otherNames, faultyOwner)
        status === OK
        res must contain(otherVendor) and contain(otherName)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${otherVendor}' and
              name = '${otherName}' and
              ispublic = true;""").first === 1
        }
      }

      """return a 401 if the owner is not a prefix of the vendor and the schema
      is private""" in {
        val (status, res) =
          schema.getMetadataFromName(vendors, names, faultyOwner)
        status === Unauthorized
        res must contain("You do not have sufficient privileges")
      }

      "return a 404 if there are no schemas matching the query" in {
        val (status, res) =
          schema.getMetadataFromName(vendors, faultyNames, owner)
        status === NotFound
        res must contain("There are no schemas for this vendor, name")

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${faultyName}';""").first === 0
        }
      }
    }

    "for getFromVendor" should {

      "return schemas properly if they are private" in {
        val (status, res) = schema.getFromVendor(vendors, owner)
        status === OK
        res must contain(innerSchema)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              ispublic = false;""").first === 1
        }
      }

      "retrieve schemas properly if they are public" in {
        val (status, res) = schema.getFromVendor(otherVendors, faultyOwner)
        status === OK
        res must contain(innerSchema)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${otherVendor}' and
              ispublic = true;""").first === 1
        }
      }

      """return a 401 if the owner is not a prefix of the vendor and the schema
      is private""" in {
        val (status, res) =
          schema.getFromVendor(vendors, faultyOwner)
        status === Unauthorized
        res must contain("You do not have sufficient privileges")
      }

      "return a 404 if there are no schemas matching the query" in {
        val (status, res) = schema.getFromVendor(faultyVendors, owner)
        status === NotFound
        res must contain("There are no schemas for this vendor")

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${faultyVendor}';""").first === 0
        }
      }
    }

    "for getMetadataFromVendor" should {

      "return schemas properly if they are private" in {
        val (status, res) = schema.getMetadataFromVendor(vendors, owner)
        status === OK
        res must contain(vendor)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
            ispublic = false;""").first === 1
        }
      }

      "retrieve schemas properly if they are public" in {
        val (status, res) =
          schema.getMetadataFromVendor(otherVendors, faultyOwner)
        status === OK
        res must contain(otherVendor)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${otherVendor}' and
              ispublic = true;""").first === 1
        }
      }

      """return a 401 if the owner is not a prefix of the vendor and the schema
      is private""" in {
        val (status, res) =
          schema.getMetadataFromVendor(vendors, faultyOwner)
        status === Unauthorized
        res must contain("You do not have sufficient privileges")
      }

      "return a 404 if there are no schemas matching the query" in {
        val (status, res) = schema.getMetadataFromVendor(faultyVendors, owner)
        status === NotFound
        res must contain("There are no schemas for this vendor")

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${faultyVendor}';""").first === 0
        }
      }
    }

    "for validate" should {

      "return a 200 if the instance is valid against the schema" in {

        val name = "ad_click3"
        schema.add(vendor, name, format, version,
          """{
            "$schema": "http://com.snowplowanalytics/schema/jsonschema/1-0-0",
            "description": "Schema for an ad click event",
            "self": {
              "vendor": "com.snowplowanalytics.snowplow",
              "name": "ad_click",
              "format": "jsonschema",
              "version": "1-0-0"
            },
            "type": "object",
            "properties": {
              "clickId": {
                "type": "string"
              },
              "impressionId": {
                "type": "string"
              },
              "zoneId": {
                "type": "string"
              },
              "bannerId": {
                "type": "string"
              },
              "campaignId": {
                "type": "string"
              },
              "advertiserId": {
                "type": "string"
              },
              "targetUrl": {
                "type": "string",
                "minLength": 1
              },
              "costModel": {
                "enum": ["cpa", "cpc", "cpm"]
              },
              "cost": {
                "type": "number",
                "minimum": 0
              }
            },
            "required": ["targetUrl"],
            "additionalProperties": false
            }
          }""", owner, permission, isPublic)
        val (status, res) =
          schema.validate(vendor, name, format, version, validInstance)
        status === OK
        res must contain("The instance provided is valid against the schema")
      }

      "return a 400 if the instance is not valid against the schema" in {
        val (status, res) =
          schema.validate(vendor, "ad_click3", format, version, invalidSchema)
        status === BadRequest
        res must
          contain("The instance provided is not valid against the schema") and
          contain("report")
      }

      "return a 400 if the instance provided is not valid" in {
        val (status, res) = schema.validate(vendor, name, format, version,
          notJson)
        status === BadRequest
        res must contain("The instance provided is not valid")
      }

      "return a 404 if the schema is not found" in {
        val (status, res) = schema.validate(faultyVendor, name, format, version,
          validInstance)
        status === NotFound
        res must contain("The schema to validate against was not found")
      }
    }

    "for validateSchema" should {

      "return the schema if it is self-describing" in {

        schema.add("com.snowplowanalytics.self-desc", "schema", format, version,
          """{
            "$schema": "http://json-schema.org/draft-04/schema#",
            "description": "Meta-schema for self-describing JSON schema",
            "self": {
              "vendor": "com.snowplowanalytics.self-desc",
              "name": "schema",
              "format": "jsonschema",
              "version": "1-0-0"
            },
            "allOf": [
            {
              "properties": {
                "self": {
                  "type": "object",
                  "properties": {
                    "vendor": {
                      "type": "string",
                      "pattern": "^[a-zA-Z0-9-_.]+$"
                    },
                    "name": {
                      "type": "string",
                      "pattern": "^[a-zA-Z0-9-_]+$"
                    },
                    "format": {
                      "type": "string",
                      "pattern": "^[a-zA-Z0-9-_]+$"
                    },
                    "version": {
                      "type": "string",
                      "pattern": "^[0-9]+-[0-9]+-[0-9]+$"
                    }
                  },
                  "required": [
                  "vendor",
                  "name",
                  "format",
                  "version"
                  ],
                  "additionalProperties": false
                }
              },
              "required": [
              "self"
              ]
            },
            {
              "$ref": "http://json-schema.org/draft-04/schema#"
            }
            ]
          }
          """, owner, permission, isPublic)

        val (status, res) = schema.validateSchema(validSchema, format)
        status === OK
        res must contain(validSchema)
      }

      "return a 200 if the schema provided is self-describing" in {
        val (status, res) = schema.validateSchema(validSchema, format, false)
        status === OK
        res must
          contain("The schema provided is a valid self-describing schema")
      }

      "return a 400 if the schema is not self-describing" in {
        val (status, res) = schema.validateSchema(invalidSchema, format)
        status === BadRequest
        res must contain("The schema provided is not a valid self-describing")
      }

      "return a 400 if the string provided is not valid" in {
        val (status, res) = schema.validateSchema(notJson, format)
        status === BadRequest
        res must contain("The schema provided is not valid")
      }

      "return a 400 if the schema format provided is not supported" in {
        val (status, res) =
          schema.validateSchema(validSchema, notSupportedFormat)
        status === BadRequest
        res must contain("The schema format provided is not supported")
      }
    }

    "for dropTable" should {

      "drop the table properly" in {
        schema.dropTable

        database withDynSession {
          Q.queryNA[Int](
            """select count(*)
            from pg_catalog.pg_tables
            where tablename = '${tableName}';""").first === 0
        }
      }
    }
  }
}
