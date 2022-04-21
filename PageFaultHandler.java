//Seth Baugh
//sbaugh@email.sc.edu
package osp.Memory;

import java.awt.*;
import java.util.*;

import osp.Hardware.*;
import osp.Threads.*;
import osp.Tasks.*;
import osp.FileSys.FileSys;
import osp.FileSys.OpenFile;
import osp.IFLModules.*;
import osp.Interrupts.*;
import osp.Utilities.*;
import osp.IFLModules.*;

/**
 * The page fault handler is responsible for handling a page
 * fault.  If a swap in or swap out operation is required, the page fault
 * handler must request the operation.
 *
 * @OSPProject Memory
 */
public class PageFaultHandler extends IflPageFaultHandler {
    /**
     * This method handles a page fault.
     * <p>
     * It must check and return if the page is valid,
     * <p>
     * It must check if the page is already being brought in by some other
     * thread, i.e., if the page's has already pagefaulted
     * (for instance, using getValidatingThread()).
     * If that is the case, the thread must be suspended on that page.
     * <p>
     * If none of the above is true, a new frame must be chosen
     * and reserved until the swap in of the requested
     * page into this frame is complete.
     * <p>
     * Note that you have to make sure that the validating thread of
     * a page is set correctly. To this end, you must set the page's
     * validating thread using setValidatingThread() when a pagefault
     * happens and you must set it back to null when the pagefault is over.
     * <p>
     * If a swap-out is necessary (because the chosen frame is
     * dirty), the victim page must be dissasociated
     * from the frame and marked invalid. After the swap-in, the
     * frame must be marked clean. The swap-ins and swap-outs
     * must are preformed using regular calls read() and write().
     * <p>
     * The student implementation should define additional methods, e.g,
     * a method to search for an available frame.
     * <p>
     * Note: multiple threads might be waiting for completion of the
     * page fault. The thread that initiated the pagefault would be
     * waiting on the IORBs that are tasked to bring the page in (and
     * to free the frame during the swapout). However, while
     * pagefault is in progress, other threads might request the same
     * page. Those threads won't cause another pagefault, of course,
     * but they would enqueue themselves on the page (a page is also
     * an Event!), waiting for the completion of the original
     * pagefault. It is thus important to call notifyThreads() on the
     * page at the end -- regardless of whether the pagefault
     * succeeded in bringing the page in or not.
     *
     * @param thread        the thread that requested a page fault
     * @param referenceType whether it is memory read or write
     * @param page          the memory page
     * @return SUCCESS is everything is fine; FAILURE if the thread
     * dies while waiting for swap in or swap out or if the page is
     * already in memory and no page fault was necessary (well, this
     * shouldn't happen, but...). In addition, if there is no frame
     * that can be allocated to satisfy the page fault, then it
     * should return NotEnoughMemory
     * @OSPProject Memory
     */

    public static int times2;

    public static int do_handlePageFault(ThreadCB thread,
                                         int referenceType,
                                         PageTableEntry page) {
        boolean check = false;
        boolean free = false;
        FrameTableEntry frame = null;
        if (page.isValid()) {                           //if its valid, than it shouldn't be here, so return
            page.notifyThreads();
            ThreadCB.dispatch();
            return FAILURE;
        }
        if (page.getValidatingThread() != null)
            thread.suspend(page);

        //check if there is a free frame and if there is one assign it to frame
        for (int i = 0; i < MMU.getFrameTableSize(); i++) {
            if (MMU.getFrame(i).getPage() == null && !MMU.getFrame(i).isReserved() && MMU.getFrame(i).getLockCount() == 0) {
                free = true;
                frame = MMU.getFrame(i);
                break;
            }
        }

        //see if there are any frames that can be swapped out
        if (!free) {
            for (int i = 0; i < MMU.getFrameTableSize(); i++) {
                if (!MMU.getFrame(i).isReserved() && MMU.getFrame(i).getLockCount() == 0) {
                    check = true;
                    break;
                }
            }
        }
        //if no free frames and none that can be swapped
        if (!check && !free) {
            page.notifyThreads();
            ThreadCB.dispatch();
            return NotEnoughMemory;

        }

        SystemEvent event = new SystemEvent("event");
        page.setValidatingThread(thread);
        thread.suspend(event);
        PageFaultHandler faultHandler = new PageFaultHandler();
        if (!free) {
            frame = faultHandler.SecondChance();                //get frame if there isn't a free one


            if (frame.getPage() == null && !frame.isReserved() && frame.getLockCount() == 0)
                free = true;
        }
        if (!free) {
            if (!frame.isReserved() && frame.getLockCount() == 0) {
                //DIRTY			If the frame isn't free and its dirty
                if (frame.isDirty()) {

                    PageTableEntry page2 = frame.getPage();

                    page2.getTask().getSwapFile().write(page2.getID(), page2, thread);
                    if (thread.getStatus() == ThreadKill) {

                        page.notifyThreads();
                        event.notifyThreads();
                        page.setValidatingThread(null);
                        ThreadCB.dispatch();

                        return FAILURE;
                    }
                    frame.setReferenced(false);
                    page2.setValid(false);
                    page2.setFrame(null);
                    frame.setPage(null);


                    frame.setReserved(page.getTask());
                    page.setFrame(frame);
                    frame.setPage(page);


                    frame.setReferenced(false);
                    page.getTask().getSwapFile().read(page.getID(), page, thread);

                    if (thread.getStatus() == ThreadKill) {


                        page.notifyThreads();
                        event.notifyThreads();
                        page.setValidatingThread(null);
                        ThreadCB.dispatch();

                        return FAILURE;
                    }
                    frame.setDirty(referenceType == MemoryWrite);

                    page.setValid(true);


                }

                //CLEAN		//if the frame isnt free but its clean
                else {


                    PageTableEntry page2 = frame.getPage();


                    frame.setPage(null);                //setPage null


                    frame.setReserved(page.getTask());
                    page.setFrame(frame);                    //change attributes of new page thats going in the frame
                    frame.setPage(page);


                    frame.setReferenced(false);


                    page.getTask().getSwapFile().read(page.getID(), page, thread);

                    if (thread.getStatus() == ThreadKill) {
                        page.notifyThreads();
                        event.notifyThreads();
                        page.setValidatingThread(null);
                        ThreadCB.dispatch();

                        return FAILURE;
                    }
                    frame.setDirty(referenceType == MemoryWrite);


                    page.setValid(true);
                }
            }


        }
        //if the frame is free
        else {

            if (frame.getPage() == null && !frame.isReserved() && frame.getLockCount() == 0) {


                //change attributes
                frame.setReserved(page.getTask());
                page.setFrame(frame);
                frame.setPage(page);


                page.getTask().getSwapFile().read(page.getID(), page, thread);

                if (thread.getStatus() == ThreadKill) {

                    page.notifyThreads();
                    page.setValidatingThread(null);
                    event.notifyThreads();
                    ThreadCB.dispatch();
                    return FAILURE;
                }
            }
        }


        frame.setPage(page);

        page.setValid(true);
        page.setValidatingThread(null);
        if (referenceType == MemoryWrite)
            frame.setDirty(true);

        frame.setDirty(false);


        frame.setUnreserved(page.getTask());            //unreserve bc page is in frame
        page.notifyThreads();
        event.notifyThreads();

        ThreadCB.dispatch();

        return SUCCESS;


    }


    int numFreeFrames() {
        int count = 0;                  //returns num of free frames
        for (int i = 0; i < MMU.getFrameTableSize(); i++) {
            if (MMU.getFrame(i).getPage() == null && !MMU.getFrame(i).isReserved() && MMU.getFrame(i).getLockCount() == 0)
                count++;
        }

        return count;
    }

    FrameTableEntry getFreeFrame() {            //returns the first free frame
        for (int i = 0; i < MMU.getFrameTableSize(); i++) {
            if (MMU.getFrame(i).getPage() == null && !MMU.getFrame(i).isReserved() && MMU.getFrame(i).getLockCount() == 0)
                return MMU.getFrame(i);
        }
        return null;
    }



    FrameTableEntry SecondChance() {
        int cycles = 0;
        FrameTableEntry frame = null;
        int firstDirty = -1;
        boolean check = true;
        //run this loop until free frames equals wantFree or until a break occurs
        while (numFreeFrames() != MMU.wantFree) {
            frame = MMU.getFrame(MMU.Cursor);

            //store first dirty frame found
            if (frame.isDirty() && frame.getLockCount() == 0 && !frame.isReserved() && check) {
                check = false;
                firstDirty = MMU.Cursor;
            }
            MMU.Cursor++;
            if (MMU.Cursor == MMU.getFrameTableSize() - 1)
                MMU.Cursor = 0;
            if (frame.isReferenced()) {                          //if its referenced clear it then start again
                frame.setReferenced(false);
                cycles++;
                if (cycles == MMU.getFrameTableSize() * 2)         //if its run through the table twice
                    break;
                continue;
            }
            //Clean Frame with reference bit false
            if (frame.getPage() != null && !frame.isReserved() && frame.getLockCount() == 0 && !frame.isDirty()) {
                frame.getPage().setValid(false);
                frame.getPage().setFrame(null);
                frame.setPage(null);
                frame.setDirty(false);

            }
            cycles++;
            if (cycles == MMU.getFrameTableSize() * 2)             //if its run through the frame table twice
                break;

        }

        //phase 2
        if (numFreeFrames() != MMU.wantFree) {
            if (firstDirty != -1)
                return MMU.getFrame(firstDirty);

            return getFreeFrame();

        }

        //phase 3
        return getFreeFrame();


    }


}






