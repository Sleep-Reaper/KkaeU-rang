package com.sleepreaper.kkaeurang.detector

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log

/**
 * 차량 신호를 받아 최신값만 보관한다. 판단은 하지 않는다.
 * - AAOS 표준 API: 차속, 기어
 * - Pleos Vehicle SDK: 핸들 각도, 방향지시등, 브레이크
 */
class VehicleSignalCollector(private val context: Context) : SignalSource {

    // 최신값 저장 (리스너는 다른 스레드에서 불리므로 @Volatile)
    @Volatile var speedKmh = 0f;        private set
    @Volatile var steeringDeg = 0f;     private set
    @Volatile var turnSignalOn = false; private set
    @Volatile var brakeOn = false;      private set
    @Volatile var isPark = true;        private set

    private val vehicle = Vehicle(context.applicationContext)
    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null

    // ── AAOS: 차속, 기어 ──
    private val carCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> {
                    speedKmh = (value.value as Float) * 3.6f      // m/s → km/h
                    Log.d(TAG, "차속 $speedKmh km/h")
                }
                VehiclePropertyIds.GEAR_SELECTION -> {
                    isPark = (value.value as Int) == VehicleGear.GEAR_PARK
                    Log.d(TAG, "P단 $isPark")
                }
            }
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.e(TAG, "AAOS 오류 propId=$propId")
        }
    }

    // ── Pleos: 핸들 ──
    // 해제할 때 같은 객체를 넘겨야 하므로 클래스 변수로 둔다.
    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) {
            steeringDeg = angle ?: steeringDeg   // null이면 이전 값 유지 (0으로 바꾸면 '핸들 고정' 오탐)
            Log.d(TAG, "핸들 $steeringDeg°")
        }

        override fun onFailed(e: Exception) {
            Log.e(TAG, "핸들 리스너 실패", e)
        }
    }

    // ── Pleos: 방향지시등 ──
    private val turnSignalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
            turnSignalOn = state != null && state != TurnSignalValue.OFF   // LEFT / RIGHT면 켜짐
            Log.d(TAG, "깜빡이 $turnSignalOn (raw=$state)")
        }

        override fun onFailed(e: Exception) {
            Log.e(TAG, "깜빡이 리스너 실패", e)
        }
    }

    // ── Pleos: 브레이크 ──
    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) {
            brakeOn = status == true
            Log.d(TAG, "브레이크 $brakeOn")
        }

        override fun onFailed(e: Exception) {
            Log.e(TAG, "브레이크 리스너 실패", e)
        }
    }

    override fun sample(now: Long) =
        Sample(now, speedKmh, steeringDeg, turnSignalOn, brakeOn, isPark)

    override fun start() {
        startAaos()
        startPleos()
    }

    override fun stop() {
        stopPleos()
        propertyManager?.unregisterCallback(carCallback)
        car?.disconnect()
        vehicle.release()
    }

    private fun startAaos() {
        car = Car.createCar(context)
        propertyManager = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        propertyManager?.registerCallback(
            carCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED,
            CarPropertyManager.SENSOR_RATE_NORMAL
        )
        propertyManager?.registerCallback(
            carCallback, VehiclePropertyIds.GEAR_SELECTION,
            CarPropertyManager.SENSOR_RATE_ONCHANGE
        )
    }

    private fun startPleos() {
        vehicle.initialize()   // register보다 먼저
        vehicle.getSteeringWheel().registerSteeringWheelAngle(steeringListener)
        vehicle.getTurnSignal().registerTurnSignalState(turnSignalListener)
        vehicle.getBrake().registerBrakePedalStatus(brakeListener)
    }

    private fun stopPleos() {
        vehicle.getSteeringWheel().unregisterSteeringWheelAngle(steeringListener)
        vehicle.getTurnSignal().unregisterTurnSignalState(turnSignalListener)
        vehicle.getBrake().unregisterBrakePedalStatus(brakeListener)
    }

    companion object {
        private const val TAG = "Kkaeu.Signal"
    }
}
