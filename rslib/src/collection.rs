use crate::err::Result;
use crate::i18n::I18n;
use crate::log::Logger;
use crate::storage::{SqliteStorage, StorageContext};
use std::path::Path;

pub fn open_collection<P: AsRef<Path>>(
    path: P,
    server: bool,
    i18n: I18n,
    log: Logger,
) -> Result<Collection> {
    let storage = SqliteStorage::open_or_create(path.as_ref())?;

    let col = Collection {
        storage,
        server,
        i18n,
        log,
    };

    Ok(col)
}

pub struct Collection {
    pub(crate) storage: SqliteStorage,
    pub(crate) server: bool,
    pub(crate) i18n: I18n,
    pub(crate) log: Logger,
}

pub(crate) enum CollectionOp {}

pub(crate) struct RequestContext<'a> {
    pub storage: StorageContext<'a>,
    pub i18n: &'a I18n,
    pub log: &'a Logger,
}

impl Collection {
    /// Call the provided closure with a RequestContext that exists for
    /// the duration of the call. The request will cache prepared sql
    /// statements, so should be passed down the call tree.
    ///
    /// This function should be used for read-only requests. To mutate
    /// the database, use transact() instead.
    pub(crate) fn with_ctx<F, R>(&self, func: F) -> Result<R>
    where
        F: FnOnce(&mut RequestContext) -> Result<R>,
    {
        let mut ctx = RequestContext {
            storage: self.storage.context(self.server),
            i18n: &self.i18n,
            log: &self.log,
        };
        func(&mut ctx)
    }

    /// Execute the provided closure in a transaction, rolling back if
    /// an error is returned.
    pub(crate) fn transact<F, R>(&self, op: Option<CollectionOp>, func: F) -> Result<R>
    where
        F: FnOnce(&mut RequestContext) -> Result<R>,
    {
        self.with_ctx(|ctx| {
            ctx.storage.begin_rust_trx()?;

            let mut res = func(ctx);

            if res.is_ok() {
                if let Err(e) = ctx.storage.commit_rust_op(op) {
                    res = Err(e);
                }
            }

            if res.is_err() {
                ctx.storage.rollback_rust_trx()?;
            }

            res
        })
    }
}
