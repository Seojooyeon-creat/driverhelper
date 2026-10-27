package ai.driverhelper.gleo

import ai.driverhelper.rule.MessageId

/** Voice FAQ topics. The `topic` enum in assets/schema.json must list exactly these keys. */
object Faq {
    val topics: Map<String, String> = mapOf(
        "right_turn" to MessageId.RIGHT_TURN_STOP,
        "school_zone" to MessageId.SCHOOL_ZONE,
        "speed_limits" to "faq_speed_limits",
        "speed_cameras" to "faq_speed_cameras",
        "seat_belt_child_seat" to "faq_seat_belt",
        "drunk_driving" to "faq_drunk_driving",
        "phone_use" to "faq_phone_use",
        "pedestrian_crosswalk" to "faq_pedestrian",
        "left_turn" to "faq_left_turn",
        "yellow_light" to "faq_yellow_light",
        "bus_lane" to "faq_bus_lane",
        "u_turn" to "faq_u_turn",
        "parking_curb_lines" to "faq_parking",
        "toll_hipass" to "faq_hipass",
        "accident" to "faq_accident",
        "refueling" to "faq_fuel",
        "highway" to "faq_highway",
        "emergency_vehicles" to "faq_emergency",
        "school_bus" to "faq_school_bus",
        "intersection_priority" to "faq_intersection",
        "driving_license" to "faq_license",
    )

    fun messageIdFor(topic: String): String? = topics[topic]
}
