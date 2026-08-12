package pro.kisscat.www.bookmarkhelper.sync.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkFolderPathRulesTest {
    @Test
    fun normalizeProducesOneCanonicalHierarchy() {
        assertEquals("工作/项目/资料", BookmarkFolderPathRules.normalize(" /工作\\项目//资料/ "))
    }

    @Test
    fun movingRootIsRejected() {
        assertEquals(
            BookmarkFolderMovePlan.Rejected(BookmarkFolderRejection.ROOT_PROTECTED),
            BookmarkFolderPathRules.move("/", "归档"),
        )
    }

    @Test
    fun movingIntoOwnDescendantIsRejected() {
        assertEquals(
            BookmarkFolderMovePlan.Rejected(BookmarkFolderRejection.WOULD_CREATE_CYCLE),
            BookmarkFolderPathRules.move("工作", "工作/项目"),
        )
    }

    @Test
    fun unsafeParentTraversalIsRejected() {
        assertEquals(
            BookmarkFolderMovePlan.Rejected(BookmarkFolderRejection.UNSAFE_PATH),
            BookmarkFolderPathRules.move("工作", "../归档"),
        )
    }

    @Test
    fun destinationKeepsFolderNameAndRelocatesDescendants() {
        val plan = BookmarkFolderPathRules.move("工作/项目", "归档")
        assertEquals(BookmarkFolderMovePlan.Move("工作/项目", "归档/项目"), plan)
        plan as BookmarkFolderMovePlan.Move
        assertEquals(
            "归档/项目/资料/设计",
            BookmarkFolderPathRules.relocate("工作/项目/资料/设计", plan.source, plan.destination),
        )
    }

    @Test
    fun movingToCurrentParentIsReportedAsNoOp() {
        val plan = BookmarkFolderPathRules.move("工作/项目", "工作")
        assertTrue(plan is BookmarkFolderMovePlan.Rejected)
        assertEquals(
            BookmarkFolderRejection.DESTINATION_UNCHANGED,
            (plan as BookmarkFolderMovePlan.Rejected).reason,
        )
    }
}
