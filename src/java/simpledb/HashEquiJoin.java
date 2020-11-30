package simpledb;

import java.util.*;

/**
 * The Join operator implements the relational join operation.
 */
public class HashEquiJoin extends Operator {

    private static final long serialVersionUID = 1L;

    private final JoinPredicate p;
    private DbIterator child1;
    private DbIterator child2;

    private final transient Map<Field, List<Tuple>> map;
    private transient Tuple tuple1;
    private transient Tuple tuple2;

    transient Iterator<Tuple> listIt = null;

    /**
     * Constructor. Accepts to children to join and the predicate to join them
     * on
     *
     * @param p      The predicate to use to join the children
     * @param child1 Iterator for the left(outer) relation to join
     * @param child2 Iterator for the right(inner) relation to join
     */
    public HashEquiJoin(JoinPredicate p, DbIterator child1, DbIterator child2) {
        this.p = p;
        this.child1 = child1;
        this.child2 = child2;
        this.map = new HashMap<>();
    }

    public JoinPredicate getJoinPredicate() {
        return p;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TupleDesc getTupleDesc() {
        return TupleDesc.merge(child1.getTupleDesc(), child2.getTupleDesc());
    }

    public String getJoinField1Name() {
        return child1.getTupleDesc().getFieldName(p.getField1());
    }

    public String getJoinField2Name() {
        return child2.getTupleDesc().getFieldName(p.getField2());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void open() throws DbException, NoSuchElementException, TransactionAbortedException {
        super.open();
        child1.open();
        child2.open();

        tuple2 = child2.hasNext() ? child2.next() : null;
        listIt = null;
        initMap();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        super.close();
        child1.close();
        child2.close();

        listIt = null;
        tuple1 = null;
        tuple2 = null;
        map.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void rewind() throws DbException, TransactionAbortedException {
        close();
        open();
    }

    /**
     * Returns the next tuple generated by the join, or null if there are no
     * more tuples. Logically, this is the next tuple in r1 cross r2 that
     * satisfies the join predicate. There are many possible implementations;
     * the simplest is a nested loops join.
     * <p>
     * Note that the tuples returned from this particular implementation of Join
     * are simply the concatenation of joining tuples from the left and right
     * relation. Therefore, there will be two copies of the join attribute in
     * the results. (Removing such duplicate columns can be done with an
     * additional projection operator if needed.)
     * <p>
     * For example, if one tuple is {1,2,3} and the other tuple is {1,5,6},
     * joined on equality of the first column, then this returns {1,2,3,1,5,6}.
     *
     * @return The next matching tuple.
     * @see JoinPredicate#filter
     */
    @Override
    protected Tuple fetchNext() throws TransactionAbortedException, DbException {
        while (!map.isEmpty()) {
            while (tuple2 != null) {
                if (listIt == null) {
                    // try to setup the list, otherwise skip
                    Field field = tuple2.getField(p.getField2());
                    if (map.containsKey(field)) {
                        listIt = map.get(field).listIterator();
                    } else {
                        tuple2 = child2.hasNext() ? child2.next() : null;
                        continue;
                    }
                }
                if (listIt.hasNext()) {
                    tuple1 = listIt.next();
                    if (p.filter(tuple1, tuple2)) {
                        Tuple tupleJoin = new Tuple(getTupleDesc());
                        int j = 0;
                        for (int i = 0; i < child1.getTupleDesc().numFields(); ++i) {
                            tupleJoin.setField(j++, tuple1.getField(i));
                        }
                        for (int i = 0; i < child2.getTupleDesc().numFields(); ++i) {
                            tupleJoin.setField(j++, tuple2.getField(i));
                        }
                        return tupleJoin;
                    }
                } else {
                    tuple2 = child2.hasNext() ? child2.next() : null;
                    listIt = null;
                }
            }
            initMap();
            child2.rewind();
            tuple2 = child2.hasNext() ? child2.next() : null;
            listIt = null;
        }
        return null;
    }

    @Override
    public DbIterator[] getChildren() {
        return new DbIterator[]{child1, child2};
    }

    @Override
    public void setChildren(DbIterator[] children) {
        child1 = children[0];
        child2 = children[1];
    }

    private void initMap() throws DbException, TransactionAbortedException {
        map.clear();
        while (child1.hasNext()) {
            tuple1 = child1.next();
            Field field = tuple1.getField(p.getField1());
            if (!map.containsKey(field)) {
                map.put(field, new ArrayList<>());
            }
            map.get(field).add(tuple1);
        }
    }
}
