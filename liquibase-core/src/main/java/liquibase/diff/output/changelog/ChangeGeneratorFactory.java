package liquibase.diff.output.changelog;

import liquibase.change.Change;
import liquibase.database.Database;
import liquibase.diff.ObjectDifferences;
import liquibase.diff.output.DiffOutputControl;
import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.servicelocator.ServiceLocator;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.snapshot.SnapshotGeneratorChain;
import liquibase.snapshot.SnapshotGeneratorFactory;
import liquibase.structure.DatabaseObject;

import java.util.*;

public class ChangeGeneratorFactory {
    private static ChangeGeneratorFactory instance;

    private List<ChangeGenerator> generators = new ArrayList<ChangeGenerator>();

    private ChangeGeneratorFactory() {
        Class[] classes;
        try {
            classes = ServiceLocator.getInstance().findClasses(ChangeGenerator.class);

            for (Class clazz : classes) {
                register((ChangeGenerator) clazz.getConstructor().newInstance());
            }

        } catch (Exception e) {
            throw new UnexpectedLiquibaseException(e);
        }

    }

    /**
     * Return singleton ChangeGeneratorFactory
     */
    public static ChangeGeneratorFactory getInstance() {
        if (instance == null) {
            instance = new ChangeGeneratorFactory();
        }
        return instance;
    }

    public static void reset() {
        instance = new ChangeGeneratorFactory();
    }


    public void register(ChangeGenerator generator) {
        generators.add(generator);
    }

    public void unregister(ChangeGenerator generator) {
        generators.remove(generator);
    }

    public void unregister(Class generatorClass) {
        ChangeGenerator toRemove = null;
        for (ChangeGenerator existingGenerator : generators) {
            if (existingGenerator.getClass().equals(generatorClass)) {
                toRemove = existingGenerator;
            }
        }

        unregister(toRemove);
    }

    protected SortedSet<ChangeGenerator> getGenerators(Class<? extends ChangeGenerator> generatorType, Class<? extends DatabaseObject> objectType, Database database) {
        SortedSet<ChangeGenerator> validGenerators = new TreeSet<ChangeGenerator>(new ChangeGeneratorComparator(objectType, database));

        for (ChangeGenerator generator : generators) {
            if (generatorType.isAssignableFrom(generator.getClass()) && generator.getPriority(objectType, database) > 0) {
                validGenerators.add(generator);
            }
        }
        return validGenerators;
    }

    private ChangeGeneratorChain createGeneratorChain(Class<? extends ChangeGenerator> generatorType, Class<? extends DatabaseObject> objectType, Database database) {
        SortedSet<ChangeGenerator> generators = getGenerators(generatorType, objectType, database);
        if (generators == null || generators.size() == 0) {
            return null;
        }
        //noinspection unchecked
        return new ChangeGeneratorChain(generators);
    }

    public Change[] fixMissing(DatabaseObject missingObject, DiffOutputControl control, Database referenceDatabase, Database comparisionDatabase) {
        ChangeGeneratorChain chain = createGeneratorChain(MissingObjectChangeGenerator.class, missingObject.getClass(), referenceDatabase);
        if (chain == null) {
            return null;
        }
        return chain.fixMissing(missingObject, control, referenceDatabase, comparisionDatabase);
    }

    public Change[] fixUnexpected(DatabaseObject unexpectedObject, DiffOutputControl control, Database referenceDatabase, Database comparisionDatabase) {
        ChangeGeneratorChain chain = createGeneratorChain(UnexpectedObjectChangeGenerator.class, unexpectedObject.getClass(), referenceDatabase);
        if (chain == null) {
            return null;
        }
        return chain.fixUnexpected(unexpectedObject, control, referenceDatabase, comparisionDatabase);
    }

    public Change[] fixChanged(DatabaseObject changedObject, ObjectDifferences differences, DiffOutputControl control, Database referenceDatabase, Database comparisionDatabase) {
        ChangeGeneratorChain chain = createGeneratorChain(UnexpectedObjectChangeGenerator.class, changedObject.getClass(), referenceDatabase);
        if (chain == null) {
            return null;
        }
        return chain.fixChanged(changedObject, differences, control, referenceDatabase, comparisionDatabase);
    }

    public Set<Class<? extends DatabaseObject>> runAfterTypes(Class<? extends DatabaseObject> objectType, Database database) {
        Set<Class<? extends DatabaseObject>> returnTypes = new HashSet<Class<? extends DatabaseObject>>();

        for (Class generatorType : new Class[]{MissingObjectChangeGenerator.class, UnexpectedObjectChangeGenerator.class, ChangedObjectChangeGenerator.class}) {
            SortedSet<ChangeGenerator> generators = getGenerators(generatorType, objectType, database);

            for (ChangeGenerator generator : generators) {
                Class<? extends DatabaseObject>[] types = generator.runAfterTypes();
                if (types != null) {
                    returnTypes.addAll(Arrays.asList(types));
                }
            }
        }
        return returnTypes;
    }

    public Set<Class<? extends DatabaseObject>> runBeforeTypes(Class<? extends DatabaseObject> objectType, Database database) {
        Set<Class<? extends DatabaseObject>> returnTypes = new HashSet<Class<? extends DatabaseObject>>();

        for (Class generatorType : new Class[]{MissingObjectChangeGenerator.class, UnexpectedObjectChangeGenerator.class, ChangedObjectChangeGenerator.class}) {
            SortedSet<ChangeGenerator> generators = getGenerators(generatorType, objectType, database);

            for (ChangeGenerator generator : generators) {
                Class<? extends DatabaseObject>[] types = generator.runBeforeTypes();
                if (types != null) {
                    returnTypes.addAll(Arrays.asList(types));
                }
            }
        }
        return returnTypes;
    }


}
