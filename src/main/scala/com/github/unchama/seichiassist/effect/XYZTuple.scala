package com.github.unchama.seichiassist.effect

import org.bukkit.{Location, World}

case class XYZTuple(x: Int, y: Int, z: Int)

trait XYZTupleSyntax {
  implicit class XYZTupleIntOps(k: Int) {
    def *(tuple: XYZTuple) = XYZTuple(tuple.x * k, tuple.y * k, tuple.z * k)
  }
  implicit class XYZTupleOps(a: XYZTuple) {
    def +(another: XYZTuple) = XYZTuple(a.x + another.x, a.y + another.y, a.z + another.z)

    def negative: XYZTuple = (-1) * a

    def -(another: XYZTuple): XYZTuple = a + another.negative

    def toLocation(world: World): Location = new Location(world, a.x.toDouble, a.y.toDouble, a.z.toDouble)
  }
}
object XYZTupleSyntax extends XYZTupleSyntax

object XYZTuple {
  def of(location: Location): XYZTuple = XYZTuple(location.getBlockX, location.getBlockY, location.getBlockZ)

  case class AxisAlignedCuboid(begin: XYZTuple, end: XYZTuple)

  implicit class AACOps(val cuboid: AxisAlignedCuboid) extends AnyVal {

    import cuboid._

    def forEachGridPoint(gridWidth: Int = 1)(action: XYZTuple => Unit): Unit = {
      def sort(a: Int, b: Int) = if (a < b) (a, b) else (b, a)

      val (xSmall, xLarge) = sort(begin.x, end.x)
      val (ySmall, yLarge) = sort(begin.y, end.y)
      val (zSmall, zLarge) = sort(begin.z, end.z)

      Range.inclusive(xSmall, xLarge, gridWidth).foreach { x =>
        Range.inclusive(ySmall, yLarge).foreach { y =>
          Range.inclusive(zSmall, zLarge).foreach { z =>
            action(XYZTuple(x, y, z))
          }
        }
      }
    }
  }

}
