package ai.driverhelper.data

/** A tappable guide entry. Its headword is message `<id>_title`, its body `<id>`; [label] is fixed text shown in every language (what the sign says). */
data class GuideItem(val id: String, val label: String? = null)

/** Content of the parked-screen guides. Texts live in assets/messages.json; this is only which entries exist. */
object Guides {
    /** The fleet car is the Hyundai IONIQ 5 (Korean spec): column shifter, EPB, regen paddles, rear-right charge port, mirror-mounted Hi-pass. */
    val controls = listOf("ctrl_start", "ctrl_gear", "ctrl_parking_brake", "ctrl_regen", "ctrl_wiper", "ctrl_lights", "ctrl_charge_port", "ctrl_hipass").map(::GuideItem)

    /** Korean words on signs and road markings that a visitor cannot read. */
    val signs = listOf(
        "sign_stop" to "정지 · 일시정지",
        "sign_slow" to "천천히",
        "sign_yield" to "양보",
        "sign_school_zone" to "어린이보호구역",
        "sign_senior_zone" to "노인보호구역",
        "sign_no_entry" to "진입금지",
        "sign_one_way" to "일방통행",
        "sign_no_parking" to "주차금지 · 주정차금지",
        "sign_tow_zone" to "견인지역",
        "sign_bus_lane" to "버스전용차로",
        "sign_unprotected_left" to "비보호",
        "sign_u_turn" to "유턴 · 보행신호시 · 좌회전시",
        "sign_right_turn_stop" to "우회전 일시정지",
        "sign_speed_camera" to "단속카메라 · 속도를 줄이시오",
        "sign_section_control" to "구간단속",
        "sign_crosswalk" to "횡단보도",
        "sign_hipass" to "하이패스",
        "sign_toll" to "요금소 · 톨게이트",
        "sign_rest_area" to "휴게소",
    ).map { (id, label) -> GuideItem(id, label) }

    /** Pleos `TelltaleType` name → explanation. Several types share one. */
    val telltales: Map<String, String> = mapOf(
        "XEV_READY" to "telltale_ready",
        "I_PEDAL" to "telltale_i_pedal",
        "EV_BATTERY_CONDITIONING" to "telltale_battery_conditioning",
        "LOW_EV_BATTERY" to "telltale_low_ev_battery",
        "EV_CHARGING_CONNECTOR" to "telltale_charging_connector",
        "TPMS" to "telltale_tpms",
        "SEAT_BELT" to "telltale_seat_belt",
        "DOOR_OPEN" to "telltale_door_open",
        "PARKING_BRAKE" to "telltale_parking_brake",
        "ELECTRONIC_PARKING_BRAKE" to "telltale_parking_brake",
        "AUTO_HOLD" to "telltale_auto_hold",
        "LOW_VOLT_BATTERY" to "telltale_12v_battery",
        "ABS" to "telltale_abs",
        "ESC" to "telltale_esc",
        "ESC_OFF" to "telltale_esc_off",
        "AIRBAG" to "telltale_airbag",
        "MASTER_WARNING" to "telltale_master_warning",
        "EV_SYSTEM" to "telltale_ev_system",
        "POWER_DOWN" to "telltale_power_down",
        "IMMOBILIZER" to "telltale_immobilizer",
        "OVER_SPEED" to "telltale_over_speed",
        "ICY_ROAD" to "telltale_icy_road",
        "HEADLAMP_HIGH" to "telltale_high_beam",
        "FRONT_FOG_LAMP" to "telltale_fog_lamp",
        "REAR_FOG_LAMP" to "telltale_fog_lamp",
    )
    val telltaleItems = telltales.values.distinct().map(::GuideItem)

    /** Spoken when the light comes on: faults and warnings only. Indicators and the normal parking-brake light stay silent. */
    val autoExplain = setOf(
        "LOW_EV_BATTERY", "EV_CHARGING_CONNECTOR", "TPMS", "DOOR_OPEN", "LOW_VOLT_BATTERY", "ABS", "AIRBAG",
        "MASTER_WARNING", "EV_SYSTEM", "POWER_DOWN", "IMMOBILIZER", "OVER_SPEED", "ICY_ROAD",
    )

    /** Return checklist rows; `check_energy` also shows the live charge level. */
    val checklist = listOf("check_energy", "check_hipass_card", "check_belongings", "check_photos", "check_park")
}
