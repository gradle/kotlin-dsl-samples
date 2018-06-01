package org.gradle.kotlin.dsl

import com.nhaarman.mockito_kotlin.*

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.PolymorphicDomainObjectContainer
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskContainer

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


class NamedDomainObjectContainerExtensionsTest {

    data class DomainObject(var foo: String? = null, var bar: Boolean? = null)

    @Test
    fun `can use monomorphic container api`() {

        val alice = DomainObject()
        val bob = DomainObject()
        val john = DomainObject()
        val container = mock<NamedDomainObjectContainer<DomainObject>> {
            on { getByName("alice") } doReturn alice
            on { create("bob") } doReturn bob
            on { maybeCreate("john") } doReturn john
        }

        // regular syntax
        container.getByName("alice") {
            it.foo = "alice-foo"
        }
        container.create("bob") {
            it.foo = "bob-foo"
        }
        container.maybeCreate("john")

        // invoke syntax
        container {
            getByName("alice") {
                it.foo = "alice-foo"
            }
            create("bob") {
                it.foo = "bob-foo"
            }
            maybeCreate("john")
        }
    }

    @Test
    fun `can use polymorphic container api`() {

        val alice = DomainObjectBase.Foo()
        val bob = DomainObjectBase.Bar()
        val default = DomainObjectBase.Default()
        val container = mock<PolymorphicDomainObjectContainer<DomainObjectBase>> {
            on { getByName("alice") } doReturn alice
            on { maybeCreate("alice", DomainObjectBase.Foo::class.java) } doReturn alice
            on { create(argThat { equals("bob") }, argThat { equals(DomainObjectBase.Bar::class.java) }, any<Action<DomainObjectBase.Bar>>()) } doReturn bob
            on { create("john", DomainObjectBase.Default::class.java) } doReturn default
        }

        // regular syntax
        container.getByName<DomainObjectBase.Foo>("alice") {
            foo = "alice-foo-2"
        }
        container.maybeCreate<DomainObjectBase.Foo>("alice")
        container.create<DomainObjectBase.Bar>("bob") {
            bar = true
        }
        container.create("john")

        // invoke syntax
        container {
            getByName<DomainObjectBase.Foo>("alice") {
                foo = "alice-foo-2"
            }
            maybeCreate<DomainObjectBase.Foo>("alice")
            create<DomainObjectBase.Bar>("bob") {
                bar = true
            }
            create("john")
        }
    }

    @Test
    fun `can configure monomorphic container`() {

        val alice = DomainObject()
        val bob = DomainObject()
        val container = mock<NamedDomainObjectContainer<DomainObject>> {
            on { create(argThat { equals("alice") }, check<Action<DomainObject>> { it(alice) }) } doReturn alice
            on { getByName("alice") } doReturn alice
            on { create(argThat { equals("bob") }, check<Action<DomainObject>> { it(bob) }) } doReturn bob
        }

        container {
            create("alice") {
                it.foo = "alice-foo"
            }
            "alice" {
                // will configure the same object as the previous block
                bar = true
            }
            create("bob") {
                it.foo = "bob-foo"
                it.bar = false
            }
        }

        assertThat(
            alice,
            equalTo(DomainObject("alice-foo", true)))

        assertThat(
            bob,
            equalTo(DomainObject("bob-foo", false)))
    }

    sealed class DomainObjectBase {
        data class Foo(var foo: String? = null) : DomainObjectBase()
        data class Bar(var bar: Boolean? = null) : DomainObjectBase()
        data class Default(val isDefault: Boolean = true) : DomainObjectBase()
    }

    @Test
    fun `can configure polymorphic container`() {

        val alice = DomainObjectBase.Foo()
        val bob = DomainObjectBase.Bar()
        val default: DomainObjectBase = DomainObjectBase.Default()
        val container = mock<PolymorphicDomainObjectContainer<DomainObjectBase>> {
            on { create(argThat { equals("alice") }, argThat { equals(DomainObjectBase.Foo::class.java) }, check<Action<DomainObjectBase.Foo>> { it(alice) }) } doReturn alice
            on { create("bob", DomainObjectBase.Bar::class.java) } doReturn bob
            on { create(argThat { equals("jim") }, any<Action<DomainObjectBase>>()) } doReturn default
            on { create("steve") } doReturn default
            on { getByName("alice") } doReturn alice
            on { getByName("bob") } doReturn bob
            on { getByName("jim") } doReturn default
            on { getByName("steve") } doReturn default
        }

        container {
            val a = create<DomainObjectBase.Foo>("alice") {
                foo = "foo"
            }
            val b = create<DomainObjectBase.Bar>("bob")
            val j = create("jim") {}
            val s = create("steve")

            assertThat(a, sameInstance(alice))
            assertThat(b, sameInstance(bob))
            assertThat(j, sameInstance(default))
            assertThat(s, sameInstance(default))

            val ga = "alice"(DomainObjectBase.Foo::class) {}
            val gb = "bob"(DomainObjectBase.Bar::class) {}
            val gj = "jim"(DomainObjectBase::class) {}
            val gs = "steve"() // can invoke without a block, but must invoke

            assertThat(ga, sameInstance(alice))
            assertThat(gb, sameInstance(bob))
            assertThat(gj, sameInstance(default))
            assertThat(gs, sameInstance(default))
        }

        assertThat(
            alice,
            equalTo(DomainObjectBase.Foo("foo")))

        assertThat(
            bob,
            equalTo(DomainObjectBase.Bar()))
    }

    @Test
    fun `can create and configure tasks`() {

        val clean = mock<Delete>()
        val tasks = mock<TaskContainer> {
            on { create(argThat { equals("clean") }, argThat { equals(Delete::class.java) }, check<Action<Delete>> { it(clean) }) } doReturn clean
            on { getByName("clean") } doReturn clean
        }

        tasks {
            create<Delete>("clean") {
                delete("some")
            }
            getByName<Delete>("clean") {
                delete("stuff")
            }
            "clean"(type = Delete::class) {
                delete("things")
            }
        }

        tasks.getByName<Delete>("clean") {
            delete("build")
        }

        inOrder(clean) {
            verify(clean).delete("some")
            verify(clean).delete("stuff")
            verify(clean).delete("things")
            verify(clean).delete("build")
        }
    }

    @Test
    fun `can create element in monomorphic container via delegated property`() {

        val container = mock<NamedDomainObjectContainer<DomainObject>> {
            on { create("domainObject") } doReturn DomainObject()
        }

        @Suppress("unused_variable")
        val domainObject by container.creating

        verify(container).create("domainObject")
    }

    @Test
    fun `can create and configure element in monomorphic container via delegated property`() {

        val element = DomainObject()
        val container = mock<NamedDomainObjectContainer<DomainObject>> {
            on { create("domainObject") } doReturn element
            on { getByName("domainObject") } doReturn element
        }

        val domainObject by container.creating {
            foo = "domain-foo"
            bar = true
        }

        verify(container).create("domainObject")
        assertThat(
            domainObject,
            equalTo(DomainObject("domain-foo", true)))
    }

    @Test
    fun `can create and configure element in polymorphic container via delegated property`() {

        val element = DomainObjectBase.Foo()
        val container = mock<PolymorphicDomainObjectContainer<DomainObjectBase>> {
            on { create("domainObject", DomainObjectBase.Foo::class.java) } doReturn element
            on { getByName("domainObject") } doReturn element
        }

        val domainObject by container.creating(type = DomainObjectBase.Foo::class) {
            foo = "domain-foo"
        }

        verify(container).create("domainObject", DomainObjectBase.Foo::class.java)
        assertThat(
            domainObject.foo,
            equalTo("domain-foo"))
    }

    @Test
    fun `can create element in polymorphic container via delegated property`() {

        val container = mock<PolymorphicDomainObjectContainer<DomainObjectBase>> {
            on { create("domainObject", DomainObjectBase.Foo::class.java) } doReturn DomainObjectBase.Foo()
        }

        @Suppress("unused_variable")
        val domainObject by container.creating(DomainObjectBase.Foo::class)

        verify(container).create("domainObject", DomainObjectBase.Foo::class.java)
    }

    @Test
    fun `can create element within configuration block via delegated property`() {
        val tasks = mock<TaskContainer> {
            on { create("hello") } doReturn mock<Task>()
        }

        tasks {
            @Suppress("unused_variable")
            val hello by creating
        }
        verify(tasks).create("hello")
    }

    @Test
    fun `can get element of specific type within configuration block via delegated property`() {
        val tasks = mock<TaskContainer> {
            on { getByName("hello") } doReturn mock<JavaExec>()
        }

        @Suppress("unused_variable")
        tasks {
            val hello: JavaExec by getting
            val ref = hello // forces the element to be accessed
        }
        verify(tasks).getByName("hello")
    }

    @Test
    fun `can create element of specific type within configuration block via delegated property`() {

        val container = mock<PolymorphicDomainObjectContainer<DomainObjectBase>> {
            on { create("domainObject", DomainObjectBase.Foo::class.java) } doReturn DomainObjectBase.Foo()
        }

        container {

            @Suppress("unused_variable")
            val domainObject by creating(type = DomainObjectBase.Foo::class)
        }

        verify(container).create("domainObject", DomainObjectBase.Foo::class.java)
    }

    @Test
    fun `can create and configure element of specific type within configuration block via delegated property`() {

        val element = DomainObjectBase.Foo()
        val container = mock<PolymorphicDomainObjectContainer<DomainObjectBase>> {
            on { create("domainObject", DomainObjectBase.Foo::class.java) } doReturn element
        }

        container {

            @Suppress("unused_variable")
            val domainObject by creating(DomainObjectBase.Foo::class) {
                foo = "domain-foo"
            }
        }

        verify(container).create("domainObject", DomainObjectBase.Foo::class.java)
        assertThat(
            element.foo,
            equalTo("domain-foo"))
    }
}
