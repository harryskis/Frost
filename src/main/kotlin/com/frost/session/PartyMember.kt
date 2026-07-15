package com.frost.session

import java.util.UUID

data class PartyMember(
    val username: String,
    var uuid: UUID? = null,
    var dungeonClass: String? = null,
    var classLevel: Int? = null,
)
