package jp.wings.nikkeibp.tfcameraapp

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface TranslateService {
    @GET("language/translate/v2")
    fun translateText(
        @Query("key") apiKey: String,
        @Query("q") text: String,
        @Query("target") targetLanguage: String
    ): Call<TranslateResponse>
}

data class TranslateResponse(val data: Data)
data class Data(val translations: List<Translation>)
data class Translation(val translatedText: String)