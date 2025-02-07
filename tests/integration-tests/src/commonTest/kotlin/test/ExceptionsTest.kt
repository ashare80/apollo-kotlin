package test

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.cache.normalized.api.MemoryCacheFactory
import com.apollographql.apollo3.cache.normalized.normalizedCache
import com.apollographql.apollo3.exception.ApolloHttpException
import com.apollographql.apollo3.exception.ApolloNetworkException
import com.apollographql.apollo3.integration.normalizer.HeroAndFriendsNamesQuery
import com.apollographql.apollo3.integration.normalizer.HeroNameQuery
import com.apollographql.apollo3.integration.normalizer.type.Episode
import com.apollographql.apollo3.mockserver.MockServer
import com.apollographql.apollo3.mockserver.enqueueString
import com.apollographql.apollo3.testing.internal.runTest
import kotlinx.coroutines.flow.toList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExceptionsTest {
  private lateinit var mockServer: MockServer
  private lateinit var apolloClient: ApolloClient

  private suspend fun setUp() {
    mockServer = MockServer()
    apolloClient = ApolloClient.Builder().serverUrl(mockServer.url()).build()
  }

  private suspend fun tearDown() {
    mockServer.close()
  }

  @Test
  fun whenQueryAndMalformedNetworkResponseAssertException() = runTest(before = { setUp() }, after = { tearDown() }) {
    mockServer.enqueueString("malformed")

    val response = apolloClient.query(HeroNameQuery()).execute()
    assertTrue(response.exception != null)
  }

  @Test
  fun whenHttpErrorAssertExecuteFails() = runTest(before = { setUp() }, after = { tearDown() }) {
    mockServer.enqueueString(statusCode = 404)

    val response = apolloClient.query(HeroNameQuery()).execute()
    val exception = response.exception
    assertTrue(exception is ApolloHttpException)
    assertEquals(404, exception.statusCode)
  }

  @Test
  fun whenNetworkErrorAssertApolloNetworkException() = runTest {
    apolloClient = ApolloClient.Builder().serverUrl("http://badhost/").build()

    val response = apolloClient.query(HeroNameQuery()).execute()
    assertTrue(response.exception is ApolloNetworkException)
  }

  @Test
  @Suppress("DEPRECATION")
  fun toFlowThrows() = runTest(before = { setUp() }, after = { tearDown() }) {
    mockServer.enqueueString("malformed")

    val throwingClient = apolloClient.newBuilder().useV3ExceptionHandling(true).build()
    var result = kotlin.runCatching {
      throwingClient.query(HeroNameQuery()).toFlow().toList()
    }
    assertNotNull(result.exceptionOrNull())
  }

  @Test
  @Suppress("DEPRECATION")
  fun toFlowDoesNotThrowOnV3() = runTest(before = { setUp() }, after = { tearDown() }) {
    mockServer.enqueueString("""
        {
          "errors": [
              {
                "message": "An error",
                "locations": [
                  {
                    "line": 1,
                    "column": 1
                  }
                ]
              }
            ]
          }
      """.trimIndent())
    val errorClient = apolloClient.newBuilder().useV3ExceptionHandling(true).build()
    val response = errorClient.query(HeroNameQuery()).toFlow().toList()
    assertTrue(response.first().errors?.isNotEmpty() ?: false)
  }

  private val PARTIAL_DATA_RESPONSE = """
      {
        "data": {
          "hero": {
            "name": "R2-D2",
            "friends": null
          }
        },
        "errors": [
            {
              "message": "Could not get friends",
              "locations": [
                {
                  "line": 1,
                  "column": 1
                }
              ],
              "path": [
                "hero",
                "friends"
              ]
            }
          ]
        }
    """.trimIndent()

  @Test
  @Suppress("DEPRECATION")
  fun v3ExceptionHandlingKeepsPartialData() = runTest(before = { setUp() }, after = { tearDown() }) {
    mockServer.enqueueString(PARTIAL_DATA_RESPONSE)
    val errorClient = apolloClient.newBuilder()
        .useV3ExceptionHandling(true)
        .build()
    val response = errorClient.query(HeroAndFriendsNamesQuery(Episode.EMPIRE)).execute()
    assertNotNull(response.data)
    assertTrue(response.errors?.isNotEmpty() == true)
  }

  @Test
  @Suppress("DEPRECATION")
  fun v3ExceptionHandlingKeepsPartialDataWithCache() = runTest(before = { setUp() }, after = { tearDown() }) {
    mockServer.enqueueString(PARTIAL_DATA_RESPONSE.trimIndent())
    val errorClient = apolloClient.newBuilder()
        .normalizedCache(MemoryCacheFactory(1024 * 1024))
        .useV3ExceptionHandling(true)
        .build()
    val response = errorClient.query(HeroAndFriendsNamesQuery(Episode.EMPIRE)).execute()
    assertNotNull(response.data)
    assertTrue(response.errors?.isNotEmpty() == true)
  }
}
