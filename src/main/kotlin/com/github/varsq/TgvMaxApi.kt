package com.github.varsq

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.response.HttpResponse
import io.ktor.client.response.readText
import mu.KotlinLogging
import java.net.URL

private val logger = KotlinLogging.logger {}
class TgvMaxApi(val auth: Authentification.ApiAuth) {

    private val client = HttpClient(Apache) {
        defaultRequest {
            header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:60.0) Gecko/20100101 Firefox/60.0")
        }
    }

    suspend fun confirmTravels() {
        val nextTravels = nextTravels()
        nextTravels.forEach { confirmTravel(it) }
    }

    private suspend fun nextTravels() : List<JsonObject> {
        val url = "https://www.tgvmax.fr/api/account/${auth.accountId}/travels/future?vendorcode=trainline"
        val json = getWithAccessToken(url)

        val parser = Parser()
        val jsonObject = parser.parse(StringBuilder(json)) as JsonObject
        travelInfo(jsonObject)

        return jsonObject.array<JsonObject>("travels")
            ?.filter { it.obj("noShow")?.boolean("afficherBoutonConfirmer")!! }!!
    }

    private fun travelInfo(jsonObject: JsonObject) {
        val travelNumber = jsonObject.int("totalVendor")!!
        val travelToConfirmNumber = jsonObject.int("nbVoyageAConfirmer") ?: 0

        logger.info { "$travelNumber travels ; $travelToConfirmNumber travels to confirm " }
    }

    private suspend fun confirmTravel(travel: JsonObject) {
        val travelId= travel.string("id")!!

        val url = "https://www.tgvmax.fr/api/account/${auth.accountId}/travels/confirm/${travelId}/?vendorcode=trainline"
        val json = getWithAccessToken(url)

        val parser = Parser()
        val jsonObject = parser.parse(StringBuilder(json)) as JsonObject
        travelInfo(jsonObject)
        //search it and check ifit is verified
        val isOK = jsonObject.array<JsonObject>("travels")
            ?.filter { it.string("id") == travelId }
            ?.all { it.obj("noShow")?.boolean("voyageConfirme")!! } !!

        if (isOK) {
            val origin = travel.obj("origin")?.string("label")!!
            val destination = travel.obj("destination")?.string("label")!!
            val departureTime = travel.string("departureDateTime")
            logger.info { "Travel confirmed : $origin -> $destination on $departureTime " }
        }
        else {
            logger.info { "Error during confirmation of travel" }
        }
    }

    private suspend fun getWithAccessToken(url: String): String {
        logger.info { "GET request: $url" }
        return client.get<HttpResponse>() {
            url(URL(url))
            header("Cookie:", auth.cookies)
            header("X-Hpy-ApiKey", auth.apiKey)
        }.readText()
    }
}