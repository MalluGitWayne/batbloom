package com.malluwayne.batbloom.data

object Tags {
    const val STUDY = "Study"
    const val FMGE = "FMGE"
    const val WORK = "Work"
    const val READING = "Reading"
    const val CRAVING = "Craving"
    const val EXERCISE = "Exercise"
    const val OTHER = "Other"
    val all = listOf(FMGE, STUDY, WORK, READING, CRAVING, EXERCISE, OTHER)

    val cravingTriggers = listOf("Coffee/tea", "After a meal", "Stress", "Boredom", "Waking up", "Friends", "Alcohol", "Study break", "Other")

    /** Question practice and grand tests: tracked like a subject, but not part of spaced revision. */
    const val MCQ = "MCQ practice"

    /** MCQ practice plus the 19 FMGE subjects. */
    val fmgeSubjects = listOf(
        MCQ,
        "Anatomy", "Physiology", "Biochemistry",
        "Pathology", "Pharmacology", "Microbiology", "Forensic Medicine",
        "Community Medicine", "ENT", "Ophthalmology",
        "Medicine", "Surgery", "Obstetrics & Gynaecology", "Paediatrics",
        "Orthopaedics", "Dermatology", "Psychiatry", "Anaesthesia", "Radiology",
    )
}
