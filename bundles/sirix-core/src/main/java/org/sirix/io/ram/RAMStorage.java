package org.sirix.io.ram;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;
import org.sirix.access.conf.ResourceConfiguration;
import org.sirix.api.PageReadTrx;
import org.sirix.exception.SirixIOException;
import org.sirix.io.Reader;
import org.sirix.io.Storage;
import org.sirix.io.Writer;
import org.sirix.io.bytepipe.ByteHandlePipeline;
import org.sirix.page.PageReference;
import org.sirix.page.RevisionRootPage;
import org.sirix.page.UberPage;
import org.sirix.page.interfaces.Page;

/**
 * In memory storage.
 *
 * @author Johannes Lichtenberger
 *
 */
public final class RAMStorage implements Storage {

  /** Storage, mapping a resource to the pageKey/page mapping. */
  private final ConcurrentMap<String, ConcurrentMap<Long, Page>> mDataStorage;

  /** Storage, mapping a resource to the revision/revision root page mapping. */
  private final ConcurrentMap<String, ConcurrentMap<Integer, RevisionRootPage>> mRevisionRootsStorage;

  /** Mapping pageKey to the page. */
  private final ConcurrentMap<Long, Page> mResourceFileStorage;

  /** Mapping revision to the page. */
  private final ConcurrentMap<Integer, RevisionRootPage> mResourceRevisionRootsStorage;

  /** The uber page key. */
  private final ConcurrentMap<Integer, Long> mUberPageKey;

  /** {@link ByteHandlePipeline} reference. */
  private final ByteHandlePipeline mHandler;

  /** {@link RAMAccess} reference. */
  private final RAMAccess mAccess;

  /** Determines if the storage already exists or not. */
  private final boolean mExists;

  /** The unique page key. */
  private long mPageKey;

  /**
   * Constructor
   *
   * @param resourceConfig {@link ResourceConfiguration} reference
   */
  public RAMStorage(final ResourceConfiguration resourceConfig) {
    mDataStorage = new ConcurrentHashMap<>();
    mRevisionRootsStorage = new ConcurrentHashMap<>();
    mHandler = resourceConfig.byteHandlePipeline;
    final String resource = resourceConfig.getResource().getFileName().toString();
    mExists = mDataStorage.containsKey(resource);
    mDataStorage.putIfAbsent(resource, new ConcurrentHashMap<>());
    mResourceFileStorage = mDataStorage.get(resource);
    mRevisionRootsStorage.putIfAbsent(resource, new ConcurrentHashMap<>());
    mResourceRevisionRootsStorage = mRevisionRootsStorage.get(resource);
    mAccess = new RAMAccess();
    mUberPageKey = new ConcurrentHashMap<>();
    mUberPageKey.put(-1, 0L);
  }

  @Override
  public Writer createWriter() throws SirixIOException {
    return mAccess;
  }

  @Override
  public Reader createReader() throws SirixIOException {
    return mAccess;
  }

  @Override
  public void close() throws SirixIOException {}

  @Override
  public ByteHandlePipeline getByteHandler() {
    return mHandler;
  }

  @Override
  public boolean exists() throws SirixIOException {
    return mExists;
  }

  /** Provides RAM access. */
  public class RAMAccess implements Writer {

    @Override
    public Writer truncate() {
      mUberPageKey.clear();
      mResourceFileStorage.clear();
      return this;
    }

    @Override
    public Page read(PageReference reference, @Nullable PageReadTrx pageReadTrx) {
      return mResourceFileStorage.get(reference.getKey());
    }

    @Override
    public PageReference readUberPageReference() {
      final Page page = mResourceFileStorage.get(mUberPageKey.get(-1));
      final PageReference uberPageReference = new PageReference();
      uberPageReference.setKey(-1);
      uberPageReference.setPage(page);
      return uberPageReference;
    }

    @Override
    public Writer write(final PageReference pageReference) throws SirixIOException {
      final Page page = pageReference.getPage();
      pageReference.setKey(mPageKey);
      mResourceFileStorage.put(mPageKey++, page);
      return this;
    }

    @Override
    public Writer writeUberPageReference(final PageReference pageReference)
        throws SirixIOException {
      final Page page = pageReference.getPage();
      pageReference.setKey(mPageKey);
      mResourceFileStorage.put(mPageKey, page);
      mUberPageKey.put(-1, mPageKey++);
      return this;
    }

    @Override
    public void close() throws SirixIOException {}

    @Override
    public Writer truncateTo(int revision) {
      PageReference uberPageReference = readUberPageReference();
      UberPage uberPage = (UberPage) uberPageReference.getPage();

      while (uberPage.getRevisionNumber() != revision) {
        mResourceFileStorage.remove(uberPageReference.getKey());
        final Long previousUberPageKey = uberPage.getPreviousUberPageKey();
        uberPage = (UberPage) read(new PageReference().setKey(previousUberPageKey), null);
        uberPageReference = new PageReference();
        uberPageReference.setKey(previousUberPageKey);

        if (uberPage.getRevisionNumber() == revision) {
          mResourceFileStorage.put(previousUberPageKey, uberPage);
          mUberPageKey.put(-1, previousUberPageKey);
          break;
        }
      }

      return this;
    }

    @Override
    public RevisionRootPage readRevisionRootPage(int revision, PageReadTrx pageReadTrx) {
      return mResourceRevisionRootsStorage.get(revision);
    }
  }
}
