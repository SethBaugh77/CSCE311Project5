//Seth Baugh
//sbaugh@email.sc.edu
package osp.Memory;
/**
 * The PageTable class represents the page table for a given task.
 * A PageTable consists of an array of PageTableEntry objects.  This
 * page table is of the non-inverted type.
 *
 * @OSPProject Memory
 */

import java.lang.Math;

import osp.Tasks.*;
import osp.Utilities.*;
import osp.IFLModules.*;
import osp.Hardware.*;

public class PageTable extends IflPageTable {
    /**
     The page table constructor. Must call

     super(ownerTask)

     as its first statement.

     @OSPProject Memory
     */
    public PageTable(TaskCB ownerTask) {

        super(ownerTask);

        this.pages = new PageTableEntry[(int) Math.pow(2, MMU.getPageAddressBits())];

        for (int i = 0; i < pages.length; i++) {
            this.pages[i] = new PageTableEntry(this, i);
        }

    }

    /**
     Frees up main memory occupied by the task.
     Then unreserves the freed pages, if necessary.

     @OSPProject Memory
     */
    public void do_deallocateMemory() {
        TaskCB task = getTask();
        for (int i = 0; i < MMU.getFrameTableSize(); i++) {
            if (MMU.getFrame(i).getPage() != null) {
                if (MMU.getFrame(i).getPage().getTask() == task) {
                    MMU.getFrame(i).setPage(null);
                    MMU.getFrame(i).setDirty(false);
                    MMU.getFrame(i).setReferenced(false);

                }
            }
        }


    }


}


    /*
       Feel free to add methods/fields to improve the readability of your code
    */



/*
      Feel free to add local classes to improve the readability of your code
*/
