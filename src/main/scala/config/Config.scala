package config

import chisel3.util._
import utils._

/**
 * @module config
 * @author ziyue
 * @usecase as global configuration file for scala
 */
object config {
  val TOP_NAME: String = s"mycpu_top"

  val DEBUG: Boolean  = false
  val DEBUG_BOOT_ADDR = 0xbfc00000L
  val DEBUG_TEST_CASE = 65

  val XLEN              : Int = 32
  val STORE_BUFFER_DEPTH: Int = 32
  val LOAD_BUFFER_DEPTH : Int = 32

  val PHY_ADDR_W = 32
  val LGC_ADDR_W = 32
  val QUEUE_LEN  = 8

  val REG_ID_W = 5

  val BOOT_ADDR      = 0xBFC00000L
  val EXCEPTION_ADDR = 0xBFC00380L

  val BC_INDEX       = 8
  val BC_INDEX_W     = 3
  val BC_LINE_SIZE   = 2
  val BC_LINE_SIZE_W = 2

  val FETCH_NUM   = 2
  val ISSUE_NUM   = 2
  val ISSUE_NUM_W = log2Up(ISSUE_NUM - 1) + 2

  val N_A_PATH_TYPE = 0

  val ALU_PATH_NUM   = 2
  val ALU_PATH_TYPE  = 1
  val ALU_PATH_NUM_W = 2

  val BJU_PATH_NUM  = 0
  val BJU_PATH_TYPE = 2

  val LSU_PATH_NUM  = 2
  val LSU_PATH_TYPE = 3

  val TOT_PATH_NUM   = ALU_PATH_NUM + BJU_PATH_NUM + LSU_PATH_NUM
  val TOT_PATH_NUM_W = 3
  val PATH_TYPE_NUM  = 4

  val PATH_NUM = Seq(0, ALU_PATH_NUM, BJU_PATH_NUM, LSU_PATH_NUM)

  val QUEUE_LEN_W = 3
  val PATH_W      = 2

  val L1_W                 = 64
  val L2_W                 = 64
  var DIV_IP_CORE: Boolean = true // indicating if the divider is from IP core
}
