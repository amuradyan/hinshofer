package prism_management

/**
  * Created by spectrum on Jun, 2018
  */
case class Facet(name: String,
                 source: String,
                 replacement: String,
                 topics:Set[String] = Set(),
                 state: Boolean,
                 creationDate: Long = System.currentTimeMillis(),
                 authorId: String
                )

case class Prism(url: String, facets: Set[Facet] = Set(), authorId: String)

object Prism {

}
