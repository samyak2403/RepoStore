package com.samyak.repostore.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFiltersTest {

    @Test
    fun buildQuery_withHasApk_retainsTopicAndroid() {
        val filters = SearchFilters(query = "MQTT Browser", hasApk = true)
        val query = filters.buildQuery()
        
        assertTrue("Query should contain topic:android", query.contains("topic:android"))
        assertTrue("Query should contain user query terms", query.contains("MQTT Browser in:name,description"))
        assertTrue("Query should exclude archived repos", query.contains("archived:false"))
    }

    @Test
    fun buildQuery_withExplicitUserQueryParam_retainsTopicAndroid() {
        val filters = SearchFilters(hasApk = true)
        val query = filters.buildQuery("MQTT Browser")
        
        assertTrue("Query should contain topic:android", query.contains("topic:android"))
        assertTrue("Query should contain user query terms", query.contains("MQTT Browser in:name,description"))
    }

    @Test
    fun buildQuery_withEmptyTopicsOverride_stillDefaultsToAndroidTopic() {
        val filters = SearchFilters(query = "MQTT Browser", topics = emptyList(), hasApk = true)
        val query = filters.buildQuery()
        
        assertTrue("Query should default to topic:android when topics list is empty", query.contains("topic:android"))
    }

    @Test
    fun buildQuery_withDefaultFilters_retainsTopicAndroid() {
        val filters = SearchFilters.DEFAULT
        val query = filters.buildQuery("MQTT Browser")
        
        assertTrue("Default filters should contain topic:android", query.contains("topic:android"))
    }

    @Test
    fun buildQuery_withLanguageAndMinStars_includesAllFilters() {
        val filters = SearchFilters(
            query = "MQTT Browser",
            language = "Kotlin",
            minStars = 50,
            hasApk = true
        )
        val query = filters.buildQuery()
        
        assertTrue("Query should contain topic:android", query.contains("topic:android"))
        assertTrue("Query should contain language filter", query.contains("language:Kotlin"))
        assertTrue("Query should contain stars filter", query.contains("stars:>=50"))
    }

    @Test
    fun buildQuery_withAndroidOnlyDisabled_omitsTopicAndIncludesReadme() {
        val filters = SearchFilters(
            query = "NetVeill",
            androidOnly = false,
            searchInReadme = true
        )
        val query = filters.buildQuery()

        assertTrue("Query should include README scope", query.contains("NetVeill in:name,description,readme"))
        assertTrue("Query should not force topic:android when androidOnly is false", !query.contains("topic:android"))
        assertTrue("Query should still exclude archived repos", query.contains("archived:false"))
    }

    @Test
    fun buildBroadQuery_andBareFallback_preserveQualifiersWithoutTopicAndroid() {
        val filters = SearchFilters(language = "Kotlin", minStars = 10)
        val broad = filters.buildBroadQuery("NetVeill", includeReadme = true)
        val bare = filters.buildBareFallbackQuery("NetVeill")

        assertEquals("NetVeill in:name,description,readme language:Kotlin stars:>=10 archived:false", broad)
        assertEquals("NetVeill language:Kotlin stars:>=10 archived:false", bare)
    }
}

