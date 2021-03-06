/*
If a type A implements serializable, and a type B that extends A does not define a serialVersionUID,
sonar flags a violation.
 */

import java.io.Serializable;
import java.util.ArrayList; // implements serializable

public class SupertypeImplementsSerializable extends ArrayList<Integer> { // Noncompliant
}
