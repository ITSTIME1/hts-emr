package com.htsemr.master.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 창구(Counter). 수납과 환불을 처리하고 따로 마감하는 단위.
 * 수납 건(payment)과 일 마감(reconciliation)이 함께 가리키는 기준 정보라 master에 둔다.
 * 무인 수납기를 창구로 볼지는 미정이다(용어집 8절).
 */
@Entity
@Table(name = "counter")
internal class Counter(
    @Column(nullable = false, unique = true)
    val code: String,

    @Column(nullable = false)
    val name: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
