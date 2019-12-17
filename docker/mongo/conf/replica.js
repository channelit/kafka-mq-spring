rs.initiate({
    _id: 'fifo',
    members: [{
        _id: 0, host: 'mongo1:27017',
        priority: 1
    }, {
        _id: 1, host: 'mongo2:27019',
        priority: 0.5
    }]
})