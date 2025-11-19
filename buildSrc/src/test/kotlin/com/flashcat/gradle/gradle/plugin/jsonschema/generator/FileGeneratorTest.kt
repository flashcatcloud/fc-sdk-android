/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.gradle.plugin.jsonschema.generator

import com.flashcat.gradle.plugin.jsonschema.Animal
import com.flashcat.gradle.plugin.jsonschema.Article
import com.flashcat.gradle.plugin.jsonschema.Bike
import com.flashcat.gradle.plugin.jsonschema.Book
import com.flashcat.gradle.plugin.jsonschema.Comment
import com.flashcat.gradle.plugin.jsonschema.Company
import com.flashcat.gradle.plugin.jsonschema.Conflict
import com.flashcat.gradle.plugin.jsonschema.Customer
import com.flashcat.gradle.plugin.jsonschema.DateTime
import com.flashcat.gradle.plugin.jsonschema.Delivery
import com.flashcat.gradle.plugin.jsonschema.Demo
import com.flashcat.gradle.plugin.jsonschema.Foo
import com.flashcat.gradle.plugin.jsonschema.Household
import com.flashcat.gradle.plugin.jsonschema.Jacket
import com.flashcat.gradle.plugin.jsonschema.Location
import com.flashcat.gradle.plugin.jsonschema.Message
import com.flashcat.gradle.plugin.jsonschema.NoOpLogger
import com.flashcat.gradle.plugin.jsonschema.Opus
import com.flashcat.gradle.plugin.jsonschema.Order
import com.flashcat.gradle.plugin.jsonschema.Paper
import com.flashcat.gradle.plugin.jsonschema.PathArrayWithInteger
import com.flashcat.gradle.plugin.jsonschema.PathArrayWithNumber
import com.flashcat.gradle.plugin.jsonschema.Person
import com.flashcat.gradle.plugin.jsonschema.Product
import com.flashcat.gradle.plugin.jsonschema.Shipping
import com.flashcat.gradle.plugin.jsonschema.Style
import com.flashcat.gradle.plugin.jsonschema.TypeDefinition
import com.flashcat.gradle.plugin.jsonschema.User
import com.flashcat.gradle.plugin.jsonschema.UserMerged
import com.flashcat.gradle.plugin.jsonschema.Version
import com.flashcat.gradle.plugin.jsonschema.Video
import com.flashcat.gradle.plugin.jsonschema.WeirdCombo
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

@RunWith(Parameterized::class)
class FileGeneratorTest(
    private val inputType: TypeDefinition,
    private val outputFile: String
) {

    @get:Rule
    val tempFolderRule = TemporaryFolder()

    lateinit var tempDir: File

    @Test
    fun `generates a Poko file`() {
        tempDir = tempFolderRule.newFolder()
        val clazz = FileGeneratorTest::class.java
        val outputPath = clazz.getResource("/output/$outputFile.kt").file
        val testedGenerator = FileGenerator(tempDir, "com.example.model", NoOpLogger())

        testedGenerator.generate(inputType)

        val generatedFile = Files.find(
            Paths.get(tempDir.toURI()),
            Integer.MAX_VALUE,
            { _, attrs -> attrs.isRegularFile }
        ).findFirst().get().toFile()

        val generatedContent = generatedFile.readText(Charsets.UTF_8)
        val expectedContent = File(outputPath).readText(Charsets.UTF_8)

        if (generatedContent != expectedContent) {
            val genLines = generatedContent.lines()
            val expLines = expectedContent.lines()
            for (i in 0 until minOf(genLines.size, expLines.size)) {
                if (genLines[i] != expLines[i]) {
                    System.err.println("--- GENERATED $outputFile.kt \n")
                    System.err.println(generatedContent)
                    throw AssertionError(
                        "File $outputFile generated from \n$inputType didn't match expectation:\n" +
                            "First error on line ${i + 1}:\n" +
                            "<<<<<<< EXPECTED\n" +
                            expLines[i] +
                            "\n=======\n" +
                            genLines[i] +
                            "\n>>>>>>> GENERATED\n"
                    )
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {1}")
        fun data(): Collection<Array<Any>> {
            return listOf(
                arrayOf(Article, "Article"),
                arrayOf(Animal, "Animal"),
                arrayOf(Bike, "Bike"),
                arrayOf(Book, "Book"),
                arrayOf(Comment, "Comment"),
                arrayOf(Company, "Company"),
                arrayOf(Conflict, "Conflict"),
                arrayOf(Customer, "Customer"),
                arrayOf(DateTime, "DateTime"),
                arrayOf(Delivery, "Delivery"),
                arrayOf(Demo, "Demo"),
                arrayOf(Foo, "Foo"),
                arrayOf(Household, "Household"),
                arrayOf(Jacket, "Jacket"),
                arrayOf(Person, "Person"),
                arrayOf(Location, "Location"),
                arrayOf(Message, "Message"),
                arrayOf(Order, "Order"),
                arrayOf(Opus, "Opus"),
                arrayOf(Paper, "Paper"),
                arrayOf(Product, "Product"),
                arrayOf(Shipping, "Shipping"),
                arrayOf(Style, "Style"),
                arrayOf(Order, "Order"),
                arrayOf(User, "User"),
                arrayOf(UserMerged, "UserMerged"),
                arrayOf(Version, "Version"),
                arrayOf(Video, "Video"),
                arrayOf(WeirdCombo, "WeirdCombo"),
                arrayOf(PathArrayWithInteger, "PathArrayWithInteger"),
                arrayOf(PathArrayWithNumber, "PathArrayWithNumber")
            )
        }
    }
}
