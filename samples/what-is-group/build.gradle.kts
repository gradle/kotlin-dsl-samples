
group = "my.group"

tasks {
    "printGroup" {
        doLast {
            println("This is my group: $group")
        }
    }
}
