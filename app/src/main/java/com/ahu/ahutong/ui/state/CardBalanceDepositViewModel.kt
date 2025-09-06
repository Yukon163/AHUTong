package com.ahu.ahutong.ui.state

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.crawler.api.ycard.YcardApi
import com.ahu.ahutong.data.crawler.model.ycard.CardInfo
import com.ahu.ahutong.data.crawler.model.ycard.PayResponse
import com.ahu.ahutong.ext.launchSafe
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CardBalanceDepositViewModel : ViewModel() {

    val TAG = "CardBalanceDepositViewModel"

    private val _account = MutableStateFlow("")
    val account: StateFlow<String> = _account

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _cardInfo = MutableStateFlow<CardInfo?>(null)

    val cardInfo: StateFlow<CardInfo?> = _cardInfo

    private var token = ""


    private val _paymentState = MutableStateFlow<PaymentState>(PaymentState.Idle)
    val paymentState: StateFlow<PaymentState> = _paymentState

    fun load() = viewModelScope.launchSafe {
        _cardInfo.value = YcardApi.API.loadCardRecharge()
        Log.e(TAG, "load: ${cardInfo.value!!.data}", )
    }


    fun charge(value: String) = viewModelScope.launchSafe {

        withContext(Dispatchers.IO) {

            _paymentState.value = PaymentState.Loading

            val accountInfo = cardInfo.value?.data?.card?.getOrNull(0)?.accinfo?.getOrNull(0)

            accountInfo?.let {

                val tranamt = value
                val appId = "56321"
                val time = getTimestamp()
                val nonce = generateNonce()
                val signType = "SHA256"
                val synjonesAuth = "bearer ${token}"
                val feeitemid = "401"
                val source = "app"
                val synAccessSource = "h5"
                val yktcard = it.type

                val formBody = FormBody.Builder()
                    .add("feeitemid", feeitemid)
                    .add("appid", appId)
                    .add("tranamt", tranamt)
                    .add("source", source)
                    .add("synjones-auth", synjonesAuth)
                    .add("yktcard", yktcard)
                    .add("synAccessSource", source)
                    .add("APP_ID", appId)
                    .add("TIMESTAMP", time)
                    .add("SIGN_TYPE", signType)
                    .add("NONCE", nonce)
                    .add(
                        "SIGN",
                        sha256("APP_ID=$appId&NONCE=$nonce&SIGN_TYPE=$signType&TIMESTAMP=$time&appid=$appId&feeitemid=$feeitemid&source=$source&synAccessSource=$synAccessSource&synjones-auth=$synjonesAuth&tranamt=$tranamt&yktcard=$yktcard&SECRET_KEY=0osTIhce7uPvDKHz6aa67bhCukaKoYl4").uppercase(),
                    )
                    .build()

                val response = YcardApi.API.getOrderThirdData(formBody)

                val regex = Regex("[?]orderid=([^&]+)")
                val match = regex.find(response.raw().request.url.toString())
                val target = match?.groupValues?.get(1)

                target?.let {

                    val time = getTimestamp()
                    val nonce = generateNonce()

                    val formBody = FormBody.Builder()
                        .add("paytypeid", "63")
                        .add("paytype", "BANKCARD")
                        .add("paystep", "2")
                        .add("orderid", it)
                        .add("redirect_url", "https://ycard.ahu.edu.cn/payment/?name=result")
                        .add("userAgent", "h5")
                        .add("APP_ID", "56321")
                        .add("TIMESTAMP", time)
                        .add("SIGN_TYPE", "SHA256")
                        .add("NONCE", nonce)
                        .add(
                            "SIGN",
                            sha256("APP_ID=56321&NONCE=$nonce&SIGN_TYPE=SHA256&TIMESTAMP=$time&orderid=$it&paystep=2&paytype=BANKCARD&paytypeid=63&redirect_url=https://ycard.ahu.edu.cn/payment/?name=result&userAgent=h5&SECRET_KEY=0osTIhce7uPvDKHz6aa67bhCukaKoYl4").uppercase(),
                        )
                        .add("synAccessSource", "h5")

                        .build()


                    try {
                        val response = YcardApi.API.pay(formBody)


                        val payResponse: PayResponse? = response.body()?.let { body ->
                            val jsonString = body.string()
                            Gson().fromJson(jsonString, PayResponse::class.java)
                        }

                        payResponse?.let {
                            if (it.code == 200) {
                                _paymentState.value = PaymentState.Success(it.data)
                                load()
                                return@withContext
                            } else {
                                _paymentState.value = PaymentState.Error(it.msg)
                                return@withContext
                            }
                        }


                    } catch (e: Exception) {
                        _paymentState.value = PaymentState.Error("异常: ${e.message}")
                        return@withContext
                    }

                }
                _paymentState.value = PaymentState.Error("异常: 未获取到订单号")
                return@withContext
            }
            _paymentState.value = PaymentState.Error("异常: 未获取到用户信息")
            return@withContext
        }


    }


    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        Log.e(TAG, "sha256: $input")
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun getTimestamp(): String {
        val formatter = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
        return formatter.format(Date())
    }


    fun generateNonce(length: Int = 11): String {
        val charset = "0123456789abcdefghijklmnopqrstuvwxyz"
        val secureRandom = SecureRandom()
        return buildString {
            repeat(length) {
                val index = secureRandom.nextInt(charset.length)
                append(charset[index])
            }
        }
    }


    fun resetPaymentState() {
        _paymentState.value = PaymentState.Idle
    }


}


sealed class PaymentState {
    object Idle : PaymentState()
    object Loading : PaymentState()
    data class Success(val orderId: String) : PaymentState()
    data class Error(val message: String) : PaymentState()
}