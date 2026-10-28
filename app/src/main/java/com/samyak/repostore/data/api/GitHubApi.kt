package com.samyak.repostore.data.api

import com.samyak.repostore.data.model.GitHubContent
import com.samyak.repostore.data.model.GitHubRelease
import com.samyak.repostore.data.model.GitHubRepo
import com.samyak.repostore.data.model.GitHubSearchResponse
import com.samyak.repostore.data.model.GitHubUser
import com.samyak.repostore.data.model.ReadmeResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubApi {

    @GET("search/repositories")
    suspend fun searchRepositories(
        @Query("q") query: String,
        @Query("sort") sort: String? = "stars",
        @Query("order") order: String = "desc",
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1
    ): GitHubSearchResponse

    @GET("repos/{owner}/{repo}")
    suspend fun getRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Cache-Control") cacheControl: String? = null
    ): GitHubRepo

    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Cache-Control") cacheControl: String? = null,
        @Query("per_page") perPage: Int = 10,
        @Query("page") page: Int = 1
    ): List<GitHubRelease>

    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Cache-Control") cacheControl: String? = null
    ): GitHubRelease

    @GET("repos/{owner}/{repo}/readme")
    suspend fun getReadme(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Cache-Control") cacheControl: String? = null
    ): ReadmeResponse

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Cache-Control") cacheControl: String? = null,
        @Path("path") path: String,
        @Query("ref") ref: String? = null
    ): List<GitHubContent>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFileContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Cache-Control") cacheControl: String? = null,
        @Path("path") path: String,
        @Query("ref") ref: String? = null
    ): GitHubContent

    @GET("repos/{owner}/{repo}/contents")
    suspend fun getRootContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Cache-Control") cacheControl: String? = null,
        @Query("ref") ref: String? = null
    ): List<GitHubContent>

    @GET("users/{username}/repos")
    suspend fun getUserRepos(
        @Path("username") username: String,
        @Query("sort") sort: String = "updated",
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1
    ): List<GitHubRepo>

    @GET("repos/{owner}/{repo}/contributors")
    suspend fun getContributors(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Cache-Control") cacheControl: String? = null,
        @Query("per_page") perPage: Int = 100
    ): List<GitHubUser>

    @GET("users/{username}")
    suspend fun getUser(
        @Path("username") username: String
    ): GitHubUser
}
