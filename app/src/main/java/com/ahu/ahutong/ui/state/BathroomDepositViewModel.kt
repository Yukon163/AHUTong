package com.ahu.ahutong.ui.state

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.AHUResponse
import com.ahu.ahutong.data.crawler.PayState
import com.ahu.ahutong.data.crawler.api.ycard.YcardApi
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.BathroomTelInfo
import com.ahu.ahutong.ext.launchSafe
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import kotlin.math.log

class BathroomDepositViewModel: ViewModel() {

    val TAG = "BathroomDepositViewModel"

    private  val _info = MutableStateFlow<AHUResponse<BathroomTelInfo>?>(null)

    val info: StateFlow<AHUResponse<BathroomTelInfo>?> = _info

    var _payState = MutableStateFlow<PayState>(PayState.Idle)

    val payState : StateFlow<PayState> = _payState

    fun resetPaymentState() {
        _payState.value = PayState.Idle
    }

    fun getBathroomInfo(bathroom:String,tel: String){
        viewModelScope.launchSafe {
            withContext(Dispatchers.IO){
                _info.value = AHURepository.getBathroomInfo(bathroom = bathroom,tel = tel)
            }
        }
    }
    val paymentSuccessEvent = MutableLiveData<Unit>()
    fun pay(bathroom:String,amount: String,password: String){

        _payState.value = PayState.InProgress
        paymentSuccessEvent.value = Unit
        var feeitemid: String? = null

        when(bathroom){
            "竹园/龙河"->{
                feeitemid = "409"
            }
            "桔园/蕙园"->{
                feeitemid = "430"
            }
            else -> {

            }
        }

        if(feeitemid==null)
            return

        if(info.value == null)
            return


        viewModelScope.launchSafe {
            withContext(Dispatchers.Default){
                info.value!!.data.map!!.data?.let{ //????
                    val data = it
                    data.myCustomInfo = "手机号：${data.telPhone}"

                    val thirdPartyJson = Gson().toJson(data)

                    var formBody = FormBody.Builder()
                        .add("feeitemid",feeitemid )
                        .add("tranamt", amount)
                        .add("flag", "choose")
                        .add("source", "app")
                        .add("paystep","0")
                        .add("abstracts","")
                        .add("third_party",thirdPartyJson)
                        .build()

                    var res = YcardApi.API.pay(formBody)
                    val jsonString = res.body()!!.string()

                    val regex = """"orderid"\s*:\s*"([^"]+)"""".toRegex()
                    val match = regex.find(jsonString)
                    val orderId = match?.groups?.get(1)?.value

                    val uuid = "da07e4442e4841cca1655cb29653a023"

                    val mapString = "1690457382"
                    val plainDigits = "0123456789"
                    val keymap = mapString.mapIndexed { index, c ->
                        c.toString() to plainDigits[index].toString()
                    }.toMap()

                    val cipherText = password.map { ch ->
                        keymap[ch.toString()] ?: ch.toString()
                    }.joinToString("")

                    formBody = FormBody.Builder()
                        .add("orderid",orderId.toString() )
                        .add("paystep","2")
                        .add("paytype","ACCOUNTTSM")
                        .add("paytypeid","64")
                        .add("userAgent","h5")
                        .add("ccctype","000")
                        .add("password",cipherText)
                        .add("uuid",uuid)
                        .add("isWX","0")
                        .build()

                    res = YcardApi.API.pay(formBody)

                    if(res.isSuccessful){
                        _info.value = AHURepository.getBathroomInfo(bathroom = bathroom,tel = it.telPhone)
                        AHUCache.savePhone(it.telPhone)
                        _payState.value = PayState.Succeeded
                    }else{
                        _payState.value = PayState.Failed
                    }
                }
            }

        }
    }

}


