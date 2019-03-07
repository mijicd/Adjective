package com.victorivri.adjective

import com.victorivri.adjective.AdjectiveMembership._
import com.victorivri.adjective.Adjective._

import org.scalatest.{FreeSpec, Matchers}

class AdjectiveSpec extends FreeSpec with Matchers {

  "Usage example" in {

    // First, we define the precise types that make up our domain/universe/ontology
    object PersonOntology {
      // `Nuanced[T]` is the building block of our type algebra
      case object DbId              extends Nuanced[Int]    ((id)=> 0 <= id && id < 2000000)
      case object Name              extends Nuanced[String] (_.matches("^[A-Z][a-zA-Z]{1,31}$"))
      case object BadName           extends Nuanced[String] (_.toLowerCase.contains("badword"))
      case object ScottishLastName  extends Nuanced[String] (_ startsWith "Mc")
      case object JewishLastName    extends Nuanced[String] (_ endsWith "berg")

      // We use boolean algebra to combine base rules into more complex rules
      // Note the prefix `~` denotes negation.
      val FirstName = Name & ~BadName
      val LastName  = FirstName & (ScottishLastName | JewishLastName)
    }

    import PersonOntology._
    import Ext._ // so we can use the convenient ~ operator

    // Our Domain is now ready to be used in ADTs and elsewhere.
    // As opposed to monadic types, the preferred way to integrate
    // Adjective is to use its "successful" type, conveniently accessible
    // through `ThisAdjective.^`
    case class Person (id: DbId.^, firstName: FirstName.^, lastName: LastName.^)

    // We test membership to an adjective using `mightDescribe`.
    // We string together the inputs, to form an easily-accessible data structure:
    // Either (list of failures, tuple of successes in order of evaluation)
    val validatedInput =
      (DbId      mightDescribe 123) ~
      (FirstName mightDescribe "Bilbo") ~
      (LastName  mightDescribe "McBeggins")

    // The tupled form allows easy application to case classes
    val validPerson = validatedInput map Person.tupled

    validPerson match {
      case Right (Person(id,fname,lname)) =>
        id.base    shouldBe 123
        fname.base shouldBe "Bilbo"
        lname.base shouldBe "McBeggins"

      case Left(_) => throw new RuntimeException()
    }

    // Using the `*` postfix notation, we can access the base types if/when we wish
    val baseTypes = validPerson map { person =>
      (person.id.base, person.firstName.base, person.lastName.base)
    }
    baseTypes shouldBe Right((123,"Bilbo","McBeggins"))

    // Using toString gives an intuitive peek at the rule algebra
    //
    // The atomic [Nuanced#toString] gets printed out.
    // Beware that both `equals` and `hashCode` are (mostly) delegated to the `toString` implementation
    validPerson.right.get.toString shouldBe
      "Person({ 123 ∈ DbId },{ Bilbo ∈ (Name & ~BadName) },{ McBeggins ∈ ((Name & ~BadName) & (ScottishLastName | JewishLastName)) })"

    // Applying an invalid set of inputs accumulates all rules that failed
    val invalid =
      (DbId      mightDescribe -1) ~
      (FirstName mightDescribe "Bilbo") ~
      (LastName  mightDescribe "Ivanov") map Person.tupled

    // We can access the failures to belong to an adjective directly
    invalid shouldBe Left(List(Excludes(DbId,-1), Excludes(LastName, "Ivanov")))

    // Slightly clunky, but we can translate exclusions to e.g. human-readable validation strings
    // Possibly using a tuple of exclusions as opposed to a simple list would make it easier.
    val exclusionMappings =
      invalid.left.map { exclusions =>
        exclusions.map { y => y match {
            case Excludes(DbId, x)     => s"Bad DB id $x"
            case Excludes(LastName, x) => s"Bad Last Name $x"
          }
        }
      }

    exclusionMappings shouldBe Left(List("Bad DB id -1", "Bad Last Name Ivanov"))
  }

  "Generate ~ Ext (copy and paste in AdjectiveMembership.Ext)" ignore {

    def genAs (i: Int) = (1 to i) map { n => s"A$n <: Adjective[N$n]" } mkString ","
    def genNs (i: Int) = (1 to i) map { n => s"N$n" } mkString ","
    def genTup (i: Int) = "(" + ( (1 to i) map { n => s"Includes[A$n,N$n]"} mkString ",") + ")"
    def genABC (i: Int) = (('a' to 'z') take i) mkString ","
    def letter (i: Int) = 'a' + (i-1) toChar

    for (i <- 2 to 21) {
      val j = i + 1
      println (
        s"""
           |implicit class TupExt${i}[${genAs(i)},${genNs(i)}] (v: Either[List[Excludes[_,_]], ${genTup(i)}]) {
           |  def ~ [A$j <: Adjective[N$j], N$j] (next: AdjectiveMembership[A$j,N$j]): Either[List[Excludes[_,_]], ${genTup(j)}] =
           |    (v, next) match {
           |      case (Right((${genABC(i)})), ${letter(j)}: Includes[A$j,N$j]) => Right((${genABC(j)}))
           |      case (Left(fails), x) => Left(fails ::: AdjectiveMembership.nonMembershipAsList(x))
           |      case (Right(_), x)    => Left(AdjectiveMembership.nonMembershipAsList(x))
           |    }
           |}
           |
       """.stripMargin.trim
      )
    }


  }
}