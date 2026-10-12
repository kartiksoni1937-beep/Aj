const mongoose = require('mongoose');

const configSchema = new mongoose.Schema({
    config_id: {
        type: String,
        required: true,
        unique: true,
        trim: true,
        uppercase: true
    },
    config_name: {
        type: String,
        required: true,
        trim: true
    },
    modules: {
        type: [{
            module: {
                type: String,
                required: true,
                trim: true
            },
            settings: {
                type: mongoose.Schema.Types.Mixed,
                default: {}
            },
            enabled: {
                type: Boolean,
                default: true
            }
        }],
        default: []
    },
    user_id: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User',
        required: true,
        index: true
    },
    is_active: {
        type: Boolean,
        default: true
    },
    is_public: {
        type: Boolean,
        default: false
    },
    description: {
        type: String,
        default: null,
        trim: true
    },
    version: {
        type: String,
        default: '1.0.0',
        trim: true
    },
    created_by: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User',
        default: null
    },
    last_modified_by: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User',
        default: null
    }
}, {
    timestamps: true
});

// Indexes for faster lookups
configSchema.index({ config_id: 1 });
configSchema.index({ user_id: 1 });
configSchema.index({ is_active: 1 });
configSchema.index({ is_public: 1 });
configSchema.index({ createdAt: -1 });

// Auto-generate config_id if not provided
configSchema.pre('save', async function(next) {
    if (this.isNew && !this.config_id) {
        try {
            const ConfigModel = this.constructor;
            const count = await ConfigModel.countDocuments();
            this.config_id = `CFG${String(count + 1).padStart(6, '0')}`;
        } catch (error) {
            return next(error);
        }
    }
    next();
});

// Method to add or update a module
configSchema.methods.addOrUpdateModule = function(moduleName, settings, enabled = true) {
    const moduleIndex = this.modules.findIndex(m => m.module === moduleName);
    
    if (moduleIndex >= 0) {
        // Update existing module
        this.modules[moduleIndex].settings = { ...this.modules[moduleIndex].settings, ...settings };
        this.modules[moduleIndex].enabled = enabled;
    } else {
        // Add new module
        this.modules.push({
            module: moduleName,
            settings: settings || {},
            enabled: enabled
        });
    }
    
    return this;
};

// Method to remove a module
configSchema.methods.removeModule = function(moduleName) {
    this.modules = this.modules.filter(m => m.module !== moduleName);
    return this;
};

// Method to get a module by name
configSchema.methods.getModule = function(moduleName) {
    return this.modules.find(m => m.module === moduleName);
};

// Method to toggle module enabled state
configSchema.methods.toggleModule = function(moduleName) {
    const module = this.modules.find(m => m.module === moduleName);
    if (module) {
        module.enabled = !module.enabled;
    }
    return this;
};

const Config = mongoose.model('Config', configSchema);

module.exports = Config;

