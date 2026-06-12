package com.example

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Socket
import java.net.SocketAddress
import java.net.InetSocketAddress

/**
 * Handles connections to a proxy server and manages traffic routing.
 */
class ProxyClient(
    private val proxyHost: String,
    private val proxyPort: Int,
    private val timeout: Int = 5000
) {
    private var socket: Socket? = null
    
    companion object {
        private const val TAG = "ProxyClient"
    }

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val sock = Socket()
            sock.soTimeout = timeout
            sock.connect(InetSocketAddress(proxyHost, proxyPort), timeout)
            socket = sock
            Log.d(TAG, "Connected to proxy server: $proxyHost:$proxyPort")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to proxy: ${e.message}", e)
            false
        }
    }

    suspend fun sendData(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            socket?.getOutputStream()?.write(data)
            socket?.getOutputStream()?.flush()
            Log.d(TAG, "Sent ${data.size} bytes to proxy")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send data to proxy: ${e.message}", e)
            false
        }
    }

    suspend fun receiveData(maxBytes: Int = 4096): ByteArray? = withContext(Dispatchers.IO) {
        return@withContext try {
            val buffer = ByteArray(maxBytes)
            val bytesRead = socket?.getInputStream()?.read(buffer) ?: -1
            if (bytesRead > 0) {
                buffer.copyOf(bytesRead)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to receive data from proxy: ${e.message}", e)
            null
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            socket?.close()
            socket = null
            Log.d(TAG, "Disconnected from proxy")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting: ${e.message}")
        }
    }

    fun isConnected(): Boolean = socket?.isConnected == true

    suspend fun forwardTraffic(
        remoteHost: String,
        remotePort: Int,
        dataHandler: suspend (ByteArray) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            // Send CONNECT request (HTTP CONNECT tunneling for proxy)
            val connectRequest = "CONNECT $remoteHost:$remotePort HTTP/1.1\r\n" +
                    "Host: $remoteHost:$remotePort\r\n" +
                    "Connection: keep-alive\r\n" +
                    "\r\n"
            
            socket?.getOutputStream()?.write(connectRequest.toByteArray())
            socket?.getOutputStream()?.flush()
            
            Log.d(TAG, "Sent CONNECT request to $remoteHost:$remotePort")
            
            // Read CONNECT response
            val responseBuffer = ByteArray(1024)
            val bytesRead = socket?.getInputStream()?.read(responseBuffer) ?: -1
            
            if (bytesRead > 0) {
                val response = String(responseBuffer, 0, bytesRead)
                if (response.contains("200") || response.contains("Connection established")) {
                    Log.d(TAG, "Proxy CONNECT established successfully")
                    
                    // Now we have a tunnel, start reading data
                    while (socket?.isConnected == true) {
                        val data = receiveData()
                        if (data != null) {
                            dataHandler(data)
                        } else {
                            break
                        }
                    }
                } else {
                    Log.w(TAG, "Proxy CONNECT failed: $response")
                }
            }
        } catch (e: Exception) {
            Log.e(Exception, "Error forwarding traffic: ${e.message}", e)
        }
    }
}
